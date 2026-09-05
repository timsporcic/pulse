package org.sporcic.pulse.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.domain.Monitor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckMetricsTest {

    io.micrometer.prometheusmetrics.PrometheusMeterRegistry registry;
    CheckMetrics metrics;

    Monitor monitor = new Monitor(1, "Example", "https://example.org", 60, true, null);

    @BeforeEach
    void setUp() {
        registry = Metrics.newRegistry();
        metrics = new CheckMetrics(registry);
    }

    @Test
    void upCheckSetsGaugeToOneAndTimesTheCheck() {
        metrics.record(monitor, new Pinger.PingResult(true, 200, 84), 84);

        var scrape = registry.scrape();
        assertTrue(scrape.contains("pulse_monitor_up{monitor=\"Example\",monitor_id=\"1\"} 1.0"));
        assertTrue(scrape.contains("pulse_check_seconds_count{monitor=\"Example\",monitor_id=\"1\"} 1"));
    }

    @Test
    void downCheckSetsGaugeToZero() {
        metrics.record(monitor, new Pinger.PingResult(true, 200, 84), 84);
        metrics.record(monitor, new Pinger.PingResult(false, null, null), 5000);

        var scrape = registry.scrape();
        assertTrue(scrape.contains("pulse_monitor_up{monitor=\"Example\",monitor_id=\"1\"} 0.0"));
        assertFalse(scrape.contains("pulse_monitor_up{monitor=\"Example\",monitor_id=\"1\"} 1.0"));
        assertTrue(scrape.contains("pulse_check_seconds_count{monitor=\"Example\",monitor_id=\"1\"} 2"));
    }

    @Test
    void registryIncludesJvmMetrics() {
        assertTrue(registry.scrape().contains("jvm_memory_used_bytes"));
    }

    @Test
    void duplicateNamesKeepIndependentStatusAndTimers() {
        var other = new Monitor(2, "Example", "https://other.example", 60, true, null);
        metrics.record(monitor, new Pinger.PingResult(true, 200, 10), 10);
        metrics.record(other, new Pinger.PingResult(false, 503, 20), 20);

        assertEquals(2, registry.find("pulse.monitor.up").gauges().size());
        assertEquals(1, registry.get("pulse.monitor.up").tag("monitor_id", "1").gauge().value());
        assertEquals(0, registry.get("pulse.monitor.up").tag("monitor_id", "2").gauge().value());
        assertEquals(1, registry.get("pulse.check.seconds").tag("monitor_id", "2").timer().count());
    }


    @Test
    void removingMonitorRemovesItsMetersWithoutAffectingDuplicateNames() {
        var other = new Monitor(2, "Example", "https://other.example", 60, true, null);
        metrics.record(monitor, new Pinger.PingResult(true, 200, 10), 10);
        metrics.record(other, new Pinger.PingResult(false, 503, 20), 20);

        metrics.remove(monitor.id());

        assertTrue(registry.find("pulse.monitor.up").tag("monitor_id", "1").gauges().isEmpty());
        assertTrue(registry.find("pulse.check.seconds").tag("monitor_id", "1").timers().isEmpty());
        assertEquals(0,
                registry.get("pulse.monitor.up").tag("monitor_id", "2").gauge().value());
    }
}
