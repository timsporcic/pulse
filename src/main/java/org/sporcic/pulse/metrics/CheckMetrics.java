package org.sporcic.pulse.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.domain.Monitor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** The check-duration timer and the per-monitor up/down gauge. */
public class CheckMetrics {

    private final MeterRegistry registry;
    private final Map<Integer, AtomicInteger> upGauges = new ConcurrentHashMap<>();

    public CheckMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(Monitor monitor, Pinger.PingResult result, long durationMs) {
        Timer.builder("pulse.check.seconds")
                .description("duration of monitor checks")
                .tag("monitor", monitor.name())
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        upGauges.computeIfAbsent(monitor.id(), id -> {
            var value = new AtomicInteger();
            registry.gauge("pulse.monitor.up", io.micrometer.core.instrument.Tags.of("monitor", monitor.name()), value);
            return value;
        }).set(result.up() ? 1 : 0);
    }
}
