package org.sporcic.pulse.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.domain.Monitor;

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
        assertTrue(scrape.contains("pulse_monitor_up{monitor=\"Example\"} 1.0"));
        assertTrue(scrape.contains("pulse_check_seconds_count{monitor=\"Example\"} 1"));
    }

    @Test
    void downCheckSetsGaugeToZero() {
        metrics.record(monitor, new Pinger.PingResult(true, 200, 84), 84);
        metrics.record(monitor, new Pinger.PingResult(false, null, null), 5000);

        var scrape = registry.scrape();
        assertTrue(scrape.contains("pulse_monitor_up{monitor=\"Example\"} 0.0"));
        assertFalse(scrape.contains("pulse_monitor_up{monitor=\"Example\"} 1.0"));
        assertTrue(scrape.contains("pulse_check_seconds_count{monitor=\"Example\"} 2"));
    }

    @Test
    void registryIncludesJvmMetrics() {
        assertTrue(registry.scrape().contains("jvm_memory_used_bytes"));
    }
}
