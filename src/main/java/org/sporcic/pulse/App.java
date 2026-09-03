package org.sporcic.pulse;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinJte;
import org.sporcic.pulse.data.Database;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.web.BoardRoutes;
import org.sporcic.pulse.web.MonitorRoutes;

import java.nio.file.Path;

public class App {

    public static Javalin create(Path dbFile) {
        var dsl = Database.open(dbFile);
        var repository = new MonitorRepository(dsl);

        return Javalin.create(config -> {
            config.concurrency.useVirtualThreads = true;
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.hostedPath = "/";
            });
            config.fileRenderer(new JavalinJte(TemplateEngine.createPrecompiled(ContentType.Html)));
            new BoardRoutes(repository).register(config);
            new MonitorRoutes(repository).register(config);
        });
    }

    public static void main(String[] args) {
        var dbFile = Path.of(System.getenv().getOrDefault("PULSE_DB", "pulse.db"));
        create(dbFile).start(7070);
    }
}
