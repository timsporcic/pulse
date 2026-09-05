package org.sporcic.pulse.jobs;

import io.javalin.Javalin;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.Database;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.notify.WebhookNotifier;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotifyDownJobTest {

    @TempDir
    Path tempDir;

    Javalin receiver;
    List<String> receivedBodies = new ArrayList<>();

    DSLContext dsl;
    MonitorRepository monitors;
    CheckRepository checks;
    NotifyDownJob job;

    @BeforeEach
    void setUp() {
        receiver = Javalin.create(config ->
                config.routes.post("/hook", ctx -> {
                    receivedBodies.add(ctx.body());
                    ctx.status(200);
                })
        ).start(0);
        dsl = Database.open(tempDir.resolve("pulse-test.db"));
        monitors = new MonitorRepository(dsl);
        checks = new CheckRepository(dsl);
        job = new NotifyDownJob(monitors, checks, new WebhookNotifier(Duration.ofMillis(10)));
    }

    @AfterEach
    void tearDown() {
        receiver.stop();
    }

    String hookUrl() {
        return "http://localhost:" + receiver.port() + "/hook";
    }

    @Test
    void deliversPayloadWithFailingCheckDetails() {
        var monitor = monitors.add("Example", "https://example.org", 60, hookUrl());
        var failingCheckId = checks.add(monitor.id(), "2026-09-03T12:00:00Z", false, 503, null);

        job.notifyDown(monitor.id(), failingCheckId);

        assertEquals(1, receivedBodies.size());
        var body = receivedBodies.get(0);
        assertTrue(body.contains("\"monitor\":\"Example\""));
        assertTrue(body.contains("\"statusCode\":503"));
        assertTrue(body.contains("\"at\":\"2026-09-03T12:00:00Z\""));
    }

    @Test
    void deletedMonitorIsIgnoredQuietly() {
        job.notifyDown(9999, 9999);

        assertTrue(receivedBodies.isEmpty());
    }

    @Test
    void delayedNotificationKeepsTheFailingCheckAfterRecovery() {
        var monitor = monitors.add("Recovered", "https://example.org", 60, hookUrl());
        var failingCheckId = checks.add(monitor.id(), "2026-09-03T12:00:00Z", false, 503, null);
        checks.add(monitor.id(), "2026-09-03T12:01:00Z", true, 200, 10);

        job.notifyDown(monitor.id(), failingCheckId);

        assertEquals(1, receivedBodies.size());
        assertTrue(receivedBodies.get(0).contains("\"statusCode\":503"));
        assertTrue(receivedBodies.get(0).contains("2026-09-03T12:00:00Z"));
    }

}
