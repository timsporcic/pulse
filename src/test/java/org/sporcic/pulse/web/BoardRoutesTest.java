package org.sporcic.pulse.web;

import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sporcic.pulse.App;

import java.net.http.HttpRequest;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoardRoutesTest {

    @TempDir
    Path tempDir;

    Path dbFile() {
        return tempDir.resolve("pulse-test.db");
    }

    private static void addMonitor(HttpClient client, String name, String url) {
        client.request("/monitors", req -> req
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(HttpRequest.BodyPublishers.ofString("name=" + name + "&url=" + url)));
    }

    @Test
    void boardPageRendersMonitorsFromDatabase() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            addMonitor(client, "Example", "https%3A%2F%2Fexample.org");

            var body = client.get("/").body().string();
            assertTrue(body.contains("Example"));
            assertTrue(body.contains("https://example.org"));
            assertTrue(body.contains("id=\"board\""));
            assertTrue(body.contains("hx-get=\"/board\""));
        });
    }

    @Test
    void boardFragmentIsPartialHtml() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            addMonitor(client, "Example", "https%3A%2F%2Fexample.org");

            var response = client.get("/board");
            assertEquals(200, response.code());
            var body = response.body().string();
            assertTrue(body.contains("Example"));
            assertFalse(body.contains("<html"));
        });
    }

    @Test
    void monitorWithoutChecksShowsPendingState() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            addMonitor(client, "Fresh", "https%3A%2F%2Ffresh.example");

            var body = client.get("/board").body().string();
            assertTrue(body.contains("waiting for first check"));
        });
    }

    @Test
    void emptyBoardInvitesAddingAMonitor() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            var body = client.get("/board").body().string();
            assertTrue(body.contains("Add your first monitor"));
        });
    }
}
