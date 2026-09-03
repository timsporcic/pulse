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

/**
 * Composition root. All wiring is explicit and lives here or in
 * {@link org.sporcic.pulse.jobs.Jobs}: no DI container, no classpath scanning.
 * {@link #create(Path)} builds the web app only (what the tests boot);
 * {@link #main(String[])} additionally starts the JobRunr background server.
 */
public class App {

    /**
     * Builds the web app with a fresh metrics registry. Convenience for tests,
     * which don't share a registry with the job scheduler.
     */
    public static Javalin create(Path dbFile) {
        return create(dbFile, org.sporcic.pulse.metrics.Metrics.newRegistry());
    }

    /**
     * Builds the Javalin app: static assets, JTE rendering (precompiled
     * templates), all routes, and the {@code /metrics} endpoint. The app is
     * not started; callers invoke {@code start(port)}.
     *
     * @param dbFile   SQLite file; created and migrated on open
     * @param registry shared with the checker job in production so check
     *                 metrics and HTTP metrics land in one scrape
     */
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

    /**
     * Starts the JobRunr background server, then the web server on port 7070.
     * The database file defaults to {@code ./pulse.db}; override with the
     * {@code PULSE_DB} environment variable.
     */
    public static void main(String[] args) {

        // Disable jOOQ's startup logo and tips
        System.setProperty("org.jooq.no-logo", "true");
        System.setProperty("org.jooq.no-tips", "true");

        var dbFile = Path.of(System.getenv().getOrDefault("PULSE_DB", "pulse.db"));
        var registry = org.sporcic.pulse.metrics.Metrics.newRegistry();
        org.sporcic.pulse.jobs.Jobs.start(dbFile, registry);
        create(dbFile, registry).start(7070);
    }
}
