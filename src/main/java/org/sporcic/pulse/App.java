package org.sporcic.pulse;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.sporcic.pulse.data.Database;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.web.MonitorRoutes;

import java.nio.file.Path;

public class App {

    public static Javalin create(Path dbFile) {
        var dsl = Database.open(dbFile);
        var monitorRoutes = new MonitorRoutes(new MonitorRepository(dsl));

        return Javalin.create(config -> {
            config.concurrency.useVirtualThreads = true;
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.hostedPath = "/";
            });
            monitorRoutes.register(config);
        });
    }

    public static void main(String[] args) {
        var dbFile = Path.of(System.getenv().getOrDefault("PULSE_DB", "pulse.db"));
        create(dbFile).start(7070);
    }
}
