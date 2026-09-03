package org.sporcic.pulse.jobs;

import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.notify.WebhookNotifier;

/** One-off JobRunr job: deliver the down notification for one monitor. */
public class NotifyDownJob {

    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final WebhookNotifier notifier;

    public NotifyDownJob(MonitorRepository monitors, CheckRepository checks, WebhookNotifier notifier) {
        this.monitors = monitors;
        this.checks = checks;
        this.notifier = notifier;
    }

    public void notifyDown(int monitorId) {
        var monitor = monitors.findById(monitorId).orElse(null);
        if (monitor == null || monitor.notifyUrl() == null || monitor.notifyUrl().isBlank()) {
            return;
        }
        var latest = checks.listForMonitor(monitorId, 1);
        var statusCode = latest.isEmpty() ? null : latest.get(0).statusCode();
        var at = latest.isEmpty() ? java.time.Instant.now().toString() : latest.get(0).checkedAt();
        notifier.notifyDown(monitor.notifyUrl(), monitor.name(), monitor.url(), statusCode, at);
    }
}
