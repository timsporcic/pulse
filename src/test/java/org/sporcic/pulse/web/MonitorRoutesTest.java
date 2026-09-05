package org.sporcic.pulse.web;

import io.javalin.testtools.HttpClient;
import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sporcic.pulse.App;
import org.sporcic.pulse.check.Pinger.PingResult;
import org.sporcic.pulse.data.Database;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.metrics.CheckMetrics;
import org.sporcic.pulse.metrics.Metrics;

import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonitorRoutesTest {

    @TempDir
    Path tempDir;

    Path dbFile() {
        return tempDir.resolve("pulse-test.db");
    }

    private static io.javalin.testtools.Response postForm(HttpClient client, String path, String form) {
        return client.request(path, req -> req
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(HttpRequest.BodyPublishers.ofString(form)));
    }

    @Test
    void addedMonitorAppearsInList() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            var created = postForm(client, "/monitors", "name=Example&url=https%3A%2F%2Fexample.org");
            assertEquals(201, created.code());
            assertEquals(List.of("monitorsChanged"), created.headers().get("HX-Trigger"));

            var list = client.get("/api/monitors");
            assertEquals(200, list.code());
            var body = list.body().string();
            assertTrue(body.contains("Example"));
            assertTrue(body.contains("https://example.org"));
        });
    }

    @Test
    void addWithoutRequiredFieldsIsRejected() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            var response = postForm(client, "/monitors", "name=NoUrl");
            assertEquals(400, response.code());
        });
    }

    @Test
    void deletedMonitorDisappearsFromList() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            var created = postForm(client, "/monitors", "name=Doomed&url=https%3A%2F%2Fdoomed.example");
            var id = created.body().string().trim();

            var deleted = client.delete("/monitors/" + id);
            assertEquals(204, deleted.code());
            assertEquals(List.of("monitorsChanged"), deleted.headers().get("HX-Trigger"));

            assertFalse(client.get("/api/monitors").body().string().contains("Doomed"));
        });
    }

    @Test
    void deletingUnknownMonitorReturns404() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            assertEquals(404, client.delete("/monitors/9999").code());
        });
    }

    @Test
    void monitorsPersistAcrossAppRestarts() {
        var dbFile = dbFile();
        JavalinTest.test(App.create(dbFile), (server, client) ->
                postForm(client, "/monitors", "name=Durable&url=https%3A%2F%2Fdurable.example"));

        JavalinTest.test(App.create(dbFile), (server, client) ->
                assertTrue(client.get("/api/monitors").body().string().contains("Durable")));
    }

    @Test
    void invalidDestinationsAndIntervalsAreRejectedWithoutSaving() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            for (var form : List.of(
                    "url=ftp://example.org", "url=https://", "url=not-a-url",
                    "url=https://user:secret@example.org", "url=https://example.org:99999",
                    "url=https://example.org?search=test", "url=https://example.org?",
                    "url=https://example.org&interval_secs=0",
                    "url=https://example.org&interval_secs=-1",
                    "url=https://example.org&notify_url=file:///tmp/hook")) {
                assertEquals(400, postForm(client, "/monitors", "name=Invalid&" + form).code(), form);
            }
            assertEquals("[]", client.get("/api/monitors").body().string());
        });
    }


    @Test
    void deletingMonitorAlsoRemovesItsMetrics() {
        var file = dbFile();
        var monitor = new MonitorRepository(Database.open(file))
                .add("Removed", "https://example.org", 60, null);
        var registry = Metrics.newRegistry();
        var metrics = new CheckMetrics(registry);
        metrics.record(monitor, new PingResult(true, 200, 10), 10);

        JavalinTest.test(App.create(file, registry, metrics), (server, client) -> {
            assertEquals(204, client.delete("/monitors/" + monitor.id()).code());
            assertFalse(client.get("/metrics").body().string().contains("pulse_monitor_up{"));
            assertFalse(client.get("/metrics").body().string().contains("pulse_check_seconds_count{"));
        });
    }

    @Test
    void whitespaceOnlyNameIsRejectedWithoutSaving() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            assertEquals(400, postForm(client, "/monitors", "name=++%09&url=https://example.org").code());
            assertEquals("[]", client.get("/api/monitors").body().string());
        });
    }

    @Test
    void queryFreePathsAndWebhookQueryStringsRemainValid() {
        JavalinTest.test(App.create(dbFile()), (server, client) -> {
            assertEquals(201, postForm(client, "/monitors",
                    "name=Example&url=https://example.org/health&notify_url=https://hooks.example/notify?token=secret").code());
        });
    }
}
