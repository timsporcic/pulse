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

    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final Pinger pinger;

    public CheckDueMonitorsJob(MonitorRepository monitors, CheckRepository checks, Pinger pinger) {
        this.monitors = monitors;
        this.checks = checks;
        this.pinger = pinger;
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
        var result = pinger.ping(monitor.url());
        checks.add(monitor.id(), Instant.now().toString(), result.up(), result.statusCode(), result.latencyMs());
        log.info("checked {} ({}): {}", monitor.name(), monitor.url(), result.up() ? "up" : "DOWN");
    }
}
