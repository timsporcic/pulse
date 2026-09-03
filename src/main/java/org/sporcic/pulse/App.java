package org.sporcic.pulse;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class App {

    public static Javalin create() {
        return Javalin.create(config -> {
            config.concurrency.useVirtualThreads = true;
            config.staticFiles.add(staticFiles -> {
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.hostedPath = "/";
            });
        });
    }

    public static void main(String[] args) {
        create().start(7070);
    }
}
