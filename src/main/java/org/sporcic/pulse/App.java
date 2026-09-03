package org.sporcic.pulse;

import io.javalin.Javalin;

public class App {

    public static Javalin create() {
        return Javalin.create(config -> {
            config.concurrency.useVirtualThreads = true;
            config.routes.get("/", ctx -> ctx.html("""
                    <!doctype html>
                    <html lang="en">
                    <head><meta charset="utf-8"><title>Pulse</title></head>
                    <body>
                      <h1>Pulse</h1>
                      <p>Lean uptime monitoring. More to come.</p>
                    </body>
                    </html>
                    """));
        });
    }

    public static void main(String[] args) {
        create().start(7070);
    }
}
