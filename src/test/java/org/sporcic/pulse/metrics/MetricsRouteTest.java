package org.sporcic.pulse.metrics;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sporcic.pulse.App;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsRouteTest {

    @TempDir
    Path tempDir;

    @Test
    void metricsEndpointServesPrometheusText() {
        JavalinTest.test(App.create(tempDir.resolve("pulse-test.db")), (server, client) -> {
            client.get("/");  // generate one http request worth of metrics

            var response = client.get("/metrics");
            assertEquals(200, response.code());
            var body = response.body().string();
            assertTrue(body.contains("jvm_memory_used_bytes"));
            assertTrue(body.contains("# HELP"));
        });
    }
}
