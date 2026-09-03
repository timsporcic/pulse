package org.sporcic.pulse.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.domain.Monitor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The two check metrics the spec asks for: a {@code pulse_check_seconds}
 * timer and a {@code pulse_monitor_up} 0/1 gauge, both tagged by monitor
 * name. Thread-safe; the checker calls {@link #record} from many virtual
 * threads at once.
 */
public class CheckMetrics {

    private final MeterRegistry registry;
    private final Map<Integer, AtomicInteger> upGauges = new ConcurrentHashMap<>();

    public CheckMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records one check. The gauge is registered lazily on a monitor's first
     * check and backed by an AtomicInteger Micrometer samples on scrape -
     * gauges must reference a live object, so the map below is what keeps
     * them from being garbage collected.
     *
     * @param durationMs wall time of the ping as measured by the caller
     *                   (covers timeouts, which have no latencyMs)
     */
    public void record(Monitor monitor, Pinger.PingResult result, long durationMs) {
        Timer.builder("pulse.check.seconds")
                .description("duration of monitor checks")
                .tag("monitor", monitor.name())
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        upGauges.computeIfAbsent(monitor.id(), id -> {
            var value = new AtomicInteger();
            registry.gauge("pulse.monitor.up", Tags.of("monitor", monitor.name()), value);
            return value;
        }).set(result.up() ? 1 : 0);
    }
}
