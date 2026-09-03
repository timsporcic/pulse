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
    void deleteReturnsFalseForUnknownId() {
        assertFalse(repository.delete(9999));
    }

    @Test
    void monitorsSurviveReopeningTheDatabase() {
        repository.add("Durable", "https://durable.example", 60, null);

        var reopened = new MonitorRepository(Database.open(dbFile));

        assertEquals(1, reopened.list().size());
        assertEquals("Durable", reopened.list().get(0).name());
    }
}
