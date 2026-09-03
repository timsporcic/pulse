package org.sporcic.pulse.notify;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WebhookNotifierTest {

    Javalin receiver;
    List<String> receivedBodies = new ArrayList<>();
    AtomicInteger failuresBeforeSuccess = new AtomicInteger(0);
    AtomicInteger requestCount = new AtomicInteger(0);

    WebhookNotifier notifier = new WebhookNotifier(Duration.ofMillis(10));

    @BeforeEach
    void startReceiver() {
        receiver = Javalin.create(config ->
                config.routes.post("/hook", ctx -> {
                    requestCount.incrementAndGet();
                    if (failuresBeforeSuccess.getAndDecrement() > 0) {
                        ctx.status(500);
                        return;
                    }
                    receivedBodies.add(ctx.body());
                    ctx.status(200);
                })
        ).start(0);
    }

    @AfterEach
    void stopReceiver() {
        receiver.stop();
    }

    String hookUrl() {
        return "http://localhost:" + receiver.port() + "/hook";
    }

    @Test
    void postsJsonPayloadOnFirstAttempt() {
        var delivered = notifier.notifyDown(hookUrl(), "Example", "https://example.org", 503, "2026-09-03T12:00:00Z");

        assertTrue(delivered);
        assertEquals(1, requestCount.get());
        var body = receivedBodies.get(0);
        assertTrue(body.contains("\"monitor\":\"Example\""));
        assertTrue(body.contains("\"url\":\"https://example.org\""));
        assertTrue(body.contains("\"status\":\"down\""));
        assertTrue(body.contains("\"statusCode\":503"));
        assertTrue(body.contains("\"at\":\"2026-09-03T12:00:00Z\""));
    }

    @Test
    void retriesUntilTheWebhookAccepts() {
        failuresBeforeSuccess.set(2);

        var delivered = notifier.notifyDown(hookUrl(), "Example", "https://example.org", null, "2026-09-03T12:00:00Z");

        assertTrue(delivered);
        assertEquals(3, requestCount.get());
    }

    @Test
    void givesUpAfterThreeAttempts() {
        failuresBeforeSuccess.set(99);

        var delivered = notifier.notifyDown(hookUrl(), "Example", "https://example.org", null, "2026-09-03T12:00:00Z");

        assertFalse(delivered);
        assertEquals(3, requestCount.get());
    }

    @Test
    void unreachableWebhookFailsWithoutThrowing() {
        var delivered = notifier.notifyDown("http://localhost:59999/hook", "Example", "https://example.org", null, "2026-09-03T12:00:00Z");

        assertFalse(delivered);
    }
}
