package org.sporcic.pulse.domain;

/**
 * A monitor as the board shows it: identity plus latest-check status and
 * lifetime uptime. All three status fields are null until the first check has
 * run; {@link #pending()} is the template's test for that state.
 *
 * @param up        latest check result; null while pending
 * @param latencyMs latency of the latest check; null while pending or when
 *                  the latest check got no response
 * @param uptimePct share of all checks that were up, 0-100; null while pending
 * @param id        the monitor's id
 * @param name      the monitor's display name
 * @param url       the monitored URL
 */
public record MonitorView(
        int id,
        String name,
        String url,
        Boolean up,
        Integer latencyMs,
        Double uptimePct
) {
    public boolean pending() {
        return up == null;
    }
}
