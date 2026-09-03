package org.sporcic.pulse.web.api;

import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sporcic.pulse.App;
import org.sporcic.pulse.data.Database;

import java.net.http.HttpRequest;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sporcic.pulse.jooq.Tables.CHECK_RESULT;

class ApiRoutesTest {

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
    void listsMonitorsAsJson() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            addMonitor(client, "Example", "https%3A%2F%2Fexample.org");

            var response = client.get("/api/monitors");
            assertEquals(200, response.code());
            assertTrue(response.headers().get("Content-Type").toString().contains("application/json"));
            var body = response.body().string();
            assertTrue(body.contains("\"name\":\"Example\""));
            assertTrue(body.contains("\"url\":\"https://example.org\""));
            assertTrue(body.contains("\"intervalSecs\":60"));
        });
    }

    @Test
    void listsChecksForMonitorAsJson() {
        var dbFile = dbFile();
        JavalinTest.test(App.create(dbFile), (server, client) -> {
            addMonitor(client, "Checked", "https%3A%2F%2Fchecked.example");

            var dsl = Database.open(dbFile);
            dsl.insertInto(CHECK_RESULT)
                    .set(CHECK_RESULT.MONITOR_ID, 1)
                    .set(CHECK_RESULT.CHECKED_AT, "2026-09-03T10:00:00Z")
                    .set(CHECK_RESULT.UP, 1)
                    .set(CHECK_RESULT.STATUS_CODE, 200)
                    .set(CHECK_RESULT.LATENCY_MS, 84)
                    .execute();

            var response = client.get("/api/monitors/1/checks");
            assertEquals(200, response.code());
            var body = response.body().string();
            assertTrue(body.contains("\"checkedAt\":\"2026-09-03T10:00:00Z\""));
            assertTrue(body.contains("\"up\":true"));
            assertTrue(body.contains("\"statusCode\":200"));
            assertTrue(body.contains("\"latencyMs\":84"));
        });
    }

    @Test
    void checksNewestFirst() {
        var dbFile = dbFile();
        JavalinTest.test(App.create(dbFile), (server, client) -> {
            addMonitor(client, "Checked", "https%3A%2F%2Fchecked.example");

            var dsl = Database.open(dbFile);
            dsl.insertInto(CHECK_RESULT)
                    .set(CHECK_RESULT.MONITOR_ID, 1)
                    .set(CHECK_RESULT.CHECKED_AT, "2026-09-03T10:00:00Z")
                    .set(CHECK_RESULT.UP, 1)
                    .execute();
            dsl.insertInto(CHECK_RESULT)
                    .set(CHECK_RESULT.MONITOR_ID, 1)
                    .set(CHECK_RESULT.CHECKED_AT, "2026-09-03T10:01:00Z")
                    .set(CHECK_RESULT.UP, 0)
                    .execute();

            var body = client.get("/api/monitors/1/checks").body().string();
            assertTrue(body.indexOf("10:01:00Z") < body.indexOf("10:00:00Z"));
        });
    }

    @Test
    void checksForUnknownMonitorReturns404() {
        JavalinTest.test(App.create(dbFile()), (server, client) ->
                assertEquals(404, client.get("/api/monitors/9999/checks").code()));
    }
}
