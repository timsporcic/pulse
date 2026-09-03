package org.sporcic.pulse.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MonitorRepositoryTest {

    @TempDir
    Path tempDir;

    Path dbFile;
    MonitorRepository repository;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("pulse-test.db");
        repository = new MonitorRepository(Database.open(dbFile));
    }

    @Test
    void addReturnsMonitorWithGeneratedId() {
        var monitor = repository.add("Example", "https://example.org", 60, null);

        assertTrue(monitor.id() > 0);
        assertEquals("Example", monitor.name());
        assertEquals("https://example.org", monitor.url());
        assertEquals(60, monitor.intervalSecs());
        assertTrue(monitor.enabled());
        assertNull(monitor.notifyUrl());
    }

    @Test
    void listReturnsAllAddedMonitors() {
        repository.add("One", "https://one.example", 60, null);
        repository.add("Two", "https://two.example", 30, "https://hooks.example/notify");

        var monitors = repository.list();

        assertEquals(2, monitors.size());
        assertEquals("One", monitors.get(0).name());
        assertEquals("Two", monitors.get(1).name());
        assertEquals("https://hooks.example/notify", monitors.get(1).notifyUrl());
    }

    @Test
    void deleteRemovesMonitor() {
        var monitor = repository.add("Doomed", "https://doomed.example", 60, null);

        assertTrue(repository.delete(monitor.id()));
        assertTrue(repository.list().isEmpty());
    }

    @Test
    void deleteRemovesMonitorWithCheckHistory() {
        var dsl = Database.open(dbFile);
        var checks = new CheckRepository(dsl);
        var monitor = repository.add("Checked", "https://checked.example", 60, null);
        checks.add(monitor.id(), "2026-09-03T12:00:00Z", true, 200, 10);

        assertTrue(repository.delete(monitor.id()));
        assertTrue(repository.list().isEmpty());
        assertTrue(checks.listForMonitor(monitor.id(), 10).isEmpty());
    }

    @Test
    void deleteReturnsFalseForUnknownId() {
        assertFalse(repository.delete(9999));
    }

    @Test
    void listViewsShowsPendingForMonitorWithoutChecks() {
        repository.add("Fresh", "https://fresh.example", 60, null);

        var views = repository.listViews();

        assertEquals(1, views.size());
        assertTrue(views.get(0).pending());
        assertNull(views.get(0).latencyMs());
        assertNull(views.get(0).uptimePct());
    }

    @Test
    void listViewsReflectsLatestCheckAndUptime() {
        var dsl = Database.open(dbFile);
        var repo = new MonitorRepository(dsl);
        var monitor = repo.add("Checked", "https://checked.example", 60, null);

        var checks = org.sporcic.pulse.jooq.Tables.CHECK_RESULT;
        // three checks: down, then up twice; latest is up with 120ms
        dsl.insertInto(checks)
                .set(checks.MONITOR_ID, monitor.id())
                .set(checks.CHECKED_AT, "2026-09-03T10:00:00Z")
                .set(checks.UP, 0)
                .set(checks.STATUS_CODE, 500)
                .set(checks.LATENCY_MS, 80)
                .execute();
        dsl.insertInto(checks)
                .set(checks.MONITOR_ID, monitor.id())
                .set(checks.CHECKED_AT, "2026-09-03T10:01:00Z")
                .set(checks.UP, 1)
                .set(checks.STATUS_CODE, 200)
                .set(checks.LATENCY_MS, 90)
                .execute();
        dsl.insertInto(checks)
                .set(checks.MONITOR_ID, monitor.id())
                .set(checks.CHECKED_AT, "2026-09-03T10:02:00Z")
                .set(checks.UP, 1)
                .set(checks.STATUS_CODE, 200)
                .set(checks.LATENCY_MS, 120)
                .execute();

        var view = repo.listViews().get(0);

        assertEquals(Boolean.TRUE, view.up());
        assertEquals(120, view.latencyMs());
        assertEquals(66.7, view.uptimePct(), 0.1);
    }

    @Test
    void monitorsSurviveReopeningTheDatabase() {
        repository.add("Durable", "https://durable.example", 60, null);

        var reopened = new MonitorRepository(Database.open(dbFile));

        assertEquals(1, reopened.list().size());
        assertEquals("Durable", reopened.list().get(0).name());
    }
}
