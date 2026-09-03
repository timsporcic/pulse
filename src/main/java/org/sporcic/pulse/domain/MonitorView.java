package org.sporcic.pulse.domain;

/**
 * A monitor as shown on the board. Status fields are null until the first
 * check has run.
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
