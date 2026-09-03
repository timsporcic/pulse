package org.sporcic.pulse;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinJte;
import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.Database;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.web.BoardRoutes;
import org.sporcic.pulse.web.MonitorRoutes;
import org.sporcic.pulse.web.api.ApiRoutes;

import java.nio.file.Path;

public class App {

    public static Javalin create(Path dbFile) {
        return create(dbFile, org.sporcic.pulse.metrics.Metrics.newRegistry());
    }

    public static Javalin create(Path dbFile, io.micrometer.prometheusmetrics.PrometheusMeterRegistry registry) {
        var dsl = Database.open(dbFile);
        var repository = new MonitorRepository(dsl);

        return Javalin.create(config -> {
            config.registerPlugin(new io.javalin.micrometer.MicrometerPlugin(micrometer ->
                    micrometer.registry = registry));
            config.routes.get("/metrics", ctx -> ctx
                    .contentType("text/plain; version=0.0.4; charset=utf-8")
                    .result(registry.scrape()));
            config.concurrency.useVirtualThreads = true;
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.hostedPath = "/";
            });
            config.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
            new BoardRoutes(repository).register(config);
            new MonitorRoutes(repository).register(config);
            new ApiRoutes(repository, new CheckRepository(dsl)).register(config);
        });
    }

    public static void main(String[] args) {
        var dbFile = Path.of(System.getenv().getOrDefault("PULSE_DB", "pulse.db"));
        var registry = org.sporcic.pulse.metrics.Metrics.newRegistry();
        org.sporcic.pulse.jobs.Jobs.start(dbFile, registry);
        create(dbFile, registry).start(7070);
    }
}
