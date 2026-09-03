package org.sporcic.pulse;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    @TempDir
    Path tempDir;

    @Test
    void servesDashboardShellAtRoot() {
        JavalinTest.test(App.create(tempDir.resolve("pulse-test.db")), (server, client) -> {
            var response = client.get("/");
            assertEquals(200, response.code());
            var body = response.body().string();
            assertTrue(body.contains("Pulse"));
            assertTrue(body.contains("id=\"board\""));
        });
    }

    @Test
    void servesVendoredHtmx() {
        JavalinTest.test(App.create(tempDir.resolve("pulse-test.db")), (server, client) -> {
            var response = client.get("/htmx.min.js");
            assertEquals(200, response.code());
        });
    }

    @Test
    void servesVendoredTailwindCss() {
        JavalinTest.test(App.create(tempDir.resolve("pulse-test.db")), (server, client) -> {
            var response = client.get("/tailwind.css");
            assertEquals(200, response.code());
        });
    }
}
