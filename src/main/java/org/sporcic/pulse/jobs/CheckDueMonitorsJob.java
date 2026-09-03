package org.sporcic.pulse.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.domain.Monitor;

import java.time.Instant;
import java.util.concurrent.Executors;

/**
 * Pings every monitor that is due and records the result. Pings run
 * concurrently on virtual threads; writes are serialized by SQLite itself.
 */
public class CheckDueMonitorsJob {

    private static final Logger log = LoggerFactory.getLogger(CheckDueMonitorsJob.class);

    /** Invoked when a monitor with a notify URL transitions UP -> DOWN. */
    public interface DownListener {
        void monitorWentDown(int monitorId);
    }

    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final Pinger pinger;
    private final DownListener onDown;
    private final org.sporcic.pulse.metrics.CheckMetrics metrics;

    public CheckDueMonitorsJob(MonitorRepository monitors, CheckRepository checks, Pinger pinger,
                               DownListener onDown, org.sporcic.pulse.metrics.CheckMetrics metrics) {
        this.monitors = monitors;
        this.checks = checks;
        this.pinger = pinger;
        this.onDown = onDown;
        this.metrics = metrics;
    }

    public void run() {
        var due = monitors.listDue(Instant.now());
        if (due.isEmpty()) {
            return;
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Monitor monitor : due) {
                executor.submit(() -> check(monitor));
            }
        }
    }

    private void check(Monitor monitor) {
        var previous = checks.listForMonitor(monitor.id(), 1);
        var wasUp = !previous.isEmpty() && previous.get(0).up();

        var started = System.nanoTime();
        var result = pinger.ping(monitor.url());
        var durationMs = (System.nanoTime() - started) / 1_000_000;
        checks.add(monitor.id(), Instant.now().toString(), result.up(), result.statusCode(), result.latencyMs());
        metrics.record(monitor, result, durationMs);
        log.info("checked {} ({}): {}", monitor.name(), monitor.url(), result.up() ? "up" : "DOWN");

        var hasNotifyUrl = monitor.notifyUrl() != null && !monitor.notifyUrl().isBlank();
        if (wasUp && !result.up() && hasNotifyUrl) {
            log.info("{} went DOWN, enqueuing notification", monitor.name());
            onDown.monitorWentDown(monitor.id());
        }
    }
}
