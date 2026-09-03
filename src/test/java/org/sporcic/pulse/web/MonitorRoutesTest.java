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

            var list = client.get("/monitors");
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

            assertFalse(client.get("/monitors").body().string().contains("Doomed"));
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
                assertTrue(client.get("/monitors").body().string().contains("Durable")));
    }
}
