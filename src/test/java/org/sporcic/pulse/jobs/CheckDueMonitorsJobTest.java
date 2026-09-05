package org.sporcic.pulse.jobs;

import io.javalin.Javalin;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.Database;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.metrics.CheckMetrics;
import org.sporcic.pulse.metrics.Metrics;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.sporcic.pulse.jooq.Tables.CHECK_RESULT;
import static org.sporcic.pulse.jooq.Tables.MONITOR;

class CheckDueMonitorsJobTest {

    @TempDir
    Path tempDir;

    Javalin target;
    DSLContext dsl;
    MonitorRepository monitors;
    CheckRepository checks;
    CheckDueMonitorsJob job;
    List<Integer> notifiedMonitorIds = new ArrayList<>();
    PrometheusMeterRegistry registry =
            Metrics.newRegistry();

    @BeforeEach
    void setUp() {
        target = Javalin.create(config -> {
            config.routes.get("/ok", ctx -> ctx.result("fine"));
            config.routes.get("/broken", ctx -> ctx.status(500));
        }).start(0);
        dsl = Database.open(tempDir.resolve("pulse-test.db"));
        monitors = new MonitorRepository(dsl);
        checks = new CheckRepository(dsl);
        job = new CheckDueMonitorsJob(monitors, checks, new Pinger(), (monitorId, checkId) -> notifiedMonitorIds.add(monitorId),
                new CheckMetrics(registry));
    }

    @AfterEach
    void tearDown() {
        target.stop();
    }

    String targetUrl(String path) {
        return "http://localhost:" + target.port() + path;
    }

    int checkCount(int monitorId) {
        return dsl.fetchCount(CHECK_RESULT, CHECK_RESULT.MONITOR_ID.eq(monitorId));
    }

    @Test
    void monitorWithoutChecksIsPingedAndResultRecorded() {
        var monitor = monitors.add("Up", targetUrl("/ok"), 60, null);

        job.run();

        var recorded = checks.listForMonitor(monitor.id(), 10);
        assertEquals(1, recorded.size());
        assertTrue(recorded.get(0).up());
        assertEquals(200, recorded.get(0).statusCode());
        assertNotNull(recorded.get(0).latencyMs());
    }

    @Test
    void downTargetIsRecordedAsDown() {
        var monitor = monitors.add("Down", targetUrl("/broken"), 60, null);

        job.run();

        var recorded = checks.listForMonitor(monitor.id(), 10);
        assertEquals(1, recorded.size());
        assertFalse(recorded.get(0).up());
        assertEquals(500, recorded.get(0).statusCode());
    }

    @Test
    void recentlyCheckedMonitorIsNotPingedAgain() {
        var monitor = monitors.add("Fresh", targetUrl("/ok"), 60, null);
        checks.add(monitor.id(), Instant.now().toString(), true, 200, 10);

        job.run();

        assertEquals(1, checkCount(monitor.id()));
    }

    @Test
    void staleMonitorIsPingedAgain() {
        var monitor = monitors.add("Stale", targetUrl("/ok"), 60, null);
        checks.add(monitor.id(), Instant.now().minus(2, ChronoUnit.MINUTES).toString(), true, 200, 10);

        job.run();

        assertEquals(2, checkCount(monitor.id()));
    }

    @Test
    void upToDownTransitionNotifiesListener() {
        var monitor = monitors.add("Flapper", targetUrl("/broken"), 60,
                "https://hooks.example/notify");
        checks.add(monitor.id(), Instant.now().minus(2, ChronoUnit.MINUTES).toString(), true, 200, 10);

        job.run();

        assertEquals(List.of(monitor.id()), notifiedMonitorIds);
    }

    @Test
    void stayingDownDoesNotNotifyAgain() {
        var monitor = monitors.add("StillDown", targetUrl("/broken"), 60,
                "https://hooks.example/notify");
        checks.add(monitor.id(), Instant.now().minus(2, ChronoUnit.MINUTES).toString(), false, 500, 10);

        job.run();

        assertTrue(notifiedMonitorIds.isEmpty());
    }

    @Test
    void firstEverCheckBeingDownDoesNotNotify() {
        monitors.add("BornDown", targetUrl("/broken"), 60, "https://hooks.example/notify");

        job.run();

        assertTrue(notifiedMonitorIds.isEmpty());
    }

    @Test
    void stayingUpDoesNotNotify() {
        var monitor = monitors.add("Healthy", targetUrl("/ok"), 60, "https://hooks.example/notify");
        checks.add(monitor.id(), Instant.now().minus(2, ChronoUnit.MINUTES).toString(), true, 200, 10);

        job.run();

        assertTrue(notifiedMonitorIds.isEmpty());
    }

    @Test
    void transitionWithoutNotifyUrlDoesNotNotify() {
        var monitor = monitors.add("Quiet", targetUrl("/broken"), 60, null);
        checks.add(monitor.id(), Instant.now().minus(2, ChronoUnit.MINUTES).toString(), true, 200, 10);

        job.run();

        assertTrue(notifiedMonitorIds.isEmpty());
    }

    @Test
    void checksAreRecordedInMetrics() {
        monitors.add("Watched", targetUrl("/ok"), 60, null);
        monitors.add("Fallen", targetUrl("/broken"), 60, null);

        job.run();

        var scrape = registry.scrape();
        assertTrue(scrape.contains("pulse_monitor_up{monitor=\"Watched\",monitor_id=\"1\"} 1.0"));
        assertTrue(scrape.contains("pulse_monitor_up{monitor=\"Fallen\",monitor_id=\"2\"} 0.0"));
        assertTrue(scrape.contains("pulse_check_seconds_count{monitor=\"Watched\",monitor_id=\"1\"} 1"));
    }

    @Test
    void disabledMonitorIsNeverPinged() {
        var monitor = monitors.add("Disabled", targetUrl("/ok"), 60, null);
        dsl.update(MONITOR).set(MONITOR.ENABLED, 0).where(MONITOR.ID.eq(monitor.id())).execute();

        job.run();

        assertEquals(0, checkCount(monitor.id()));
    }

    @Test
    void unexpectedCheckFailureFailsTheSweep() {
        monitors.add("Broken checker", targetUrl("/ok"), 60, null);
        var failing = new CheckDueMonitorsJob(monitors, checks, new Pinger() {
            @Override
            public PingResult ping(String url) {
                throw new IllegalStateException("checker failed");
            }
        }, (monitorId, checkId) -> notifiedMonitorIds.add(monitorId), new CheckMetrics(registry));

        var failure = assertThrows(IllegalStateException.class, failing::run);
        assertEquals("checker failed", failure.getCause().getMessage());
    }


    @Test
    void sweepRemovesMetricsForDeletedMonitorsEvenWhenNothingIsDue() {
        var monitor = monitors.add("Removed", targetUrl("/ok"), 60, null);
        job.run();
        monitors.delete(monitor.id());

        job.run();

        assertTrue(registry.find("pulse.monitor.up").gauges().isEmpty());
        assertTrue(registry.find("pulse.check.seconds").timers().isEmpty());
    }
}
