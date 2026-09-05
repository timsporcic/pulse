package org.sporcic.pulse.jobs;

import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.notify.WebhookNotifier;

/**
 * One-off JobRunr job: deliver the down notification for one monitor. Because
 * the enqueued job persists in SQLite, a pending notification survives a crash
 * or redeploy between detection and delivery.
 */
public class NotifyDownJob {

    private final MonitorRepository monitors;
    private final CheckRepository checks;
    private final WebhookNotifier notifier;

    public NotifyDownJob(MonitorRepository monitors, CheckRepository checks, WebhookNotifier notifier) {
        this.monitors = monitors;
        this.checks = checks;
        this.notifier = notifier;
    }

    /**
     * Delivers the failing check captured at enqueue time, even if a later
     * check recovered. Deleted monitors and removed notification URLs are skipped.
     */
    public void notifyDown(int monitorId, int checkId) {
        var monitor = monitors.findById(monitorId).orElse(null);
        if (monitor == null || monitor.notifyUrl() == null || monitor.notifyUrl().isBlank()) {
            return;
        }
        var check = checks.findById(checkId).orElse(null);
        if (check == null || check.monitorId() != monitorId || check.up()) {
            return;
        }
        notifier.notifyDown(monitor.notifyUrl(), monitor.name(), monitor.url(), check.statusCode(), check.checkedAt());
    }
}
