package org.sporcic.pulse.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.domain.Monitor;
import org.sporcic.pulse.metrics.CheckMetrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * The recurring heart of Pulse: pings every monitor that is due, records each
 * result, and reports UP-to-DOWN transitions. JobRunr runs {@link #run()}
 * every 15 seconds; {@code MonitorRepository.listDue} keeps that cadence from
 * out-pacing any monitor's own interval. Pings run concurrently on virtual
 * threads; writes are serialized by SQLite itself.
 */
public class CheckDueMonitorsJob {

    private static final Logger log = LoggerFactory.getLogger(CheckDueMonitorsJob.class);

    /**
     * Invoked when a monitor with a notify URL transitions UP -> DOWN. Fires
     * only on the transition edge: staying down, or a first-ever check that is
     * down, stays silent. Production wires this to a JobRunr enqueue of
     * {@link NotifyDownJob}; tests pass a recording lambda.
     */
    public interface DownListener {
        void monitorWentDown(int monitorId, int checkId);
    }

    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final Pinger pinger;
    private final DownListener onDown;
    private final CheckMetrics metrics;

    public CheckDueMonitorsJob(MonitorRepository monitors, CheckRepository checks, Pinger pinger,
                               DownListener onDown, CheckMetrics metrics) {
        this.monitors = monitors;
        this.checks = checks;
        this.pinger = pinger;
        this.onDown = onDown;
        this.metrics = metrics;
    }

    /**
     * One sweep: fetch the due list, ping each monitor on its own virtual
     * thread, and wait for all of them (the try-with-resources close joins the
     * executor). Blocking until done is intentional - JobRunr should not mark
     * the job complete while pings are still in flight.
     */
    public void run() {
        var due = monitors.listDue(Instant.now());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = new ArrayList<Future<?>>();
            for (Monitor monitor : due) {
                tasks.add(executor.submit(() -> check(monitor)));
            }
            for (var task : tasks) {
                try {
                    task.get();
                } catch (InterruptedException e) {
                    tasks.forEach(future -> future.cancel(true));
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("monitor sweep interrupted", e);
                } catch (ExecutionException e) {
                    throw new IllegalStateException("monitor check failed", e.getCause());
                }
            }
        } finally {
            metrics.retainMonitors(monitors.list().stream().map(Monitor::id).collect(Collectors.toSet()));
        }
    }

    /**
     * Pings one monitor and records the outcome. The previous check is read
     * BEFORE the new row is written - that ordering is what makes transition
     * detection work. Safe because each monitor is checked by exactly one
     * thread per sweep and sweeps never overlap.
     */
    private void check(Monitor monitor) {
        var previous = checks.listForMonitor(monitor.id(), 1);
        var wasUp = !previous.isEmpty() && previous.get(0).up();

        var started = System.nanoTime();
        var result = pinger.ping(monitor.url());
        var durationMs = (System.nanoTime() - started) / 1_000_000;
        var checkId = checks.add(monitor.id(), Instant.now().toString(), result.up(), result.statusCode(), result.latencyMs());
        metrics.record(monitor, result, durationMs);
        log.info("checked {} ({}): {}", monitor.name(), monitor.url(), result.up() ? "up" : "DOWN");

        var hasNotifyUrl = monitor.notifyUrl() != null && !monitor.notifyUrl().isBlank();
        if (wasUp && !result.up() && hasNotifyUrl) {
            log.info("{} went DOWN, enqueuing notification", monitor.name());
            onDown.monitorWentDown(monitor.id(), checkId);
        }
    }
}
