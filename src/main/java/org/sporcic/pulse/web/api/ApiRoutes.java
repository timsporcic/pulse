package org.sporcic.pulse.web.api;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.MonitorRepository;

/**
 * The read-only JSON API: {@code GET /api/monitors} and
 * {@code GET /api/monitors/{id}/checks} (newest first, capped at
 * {@link #MAX_CHECKS}, 404 for an unknown monitor). The domain records
 * serialize directly as DTOs, so their field names are the wire contract.
 */
public class ApiRoutes {

    /** Cap per checks request; history is unbounded but responses are not. */
    private static final int MAX_CHECKS = 100;

    private final MonitorRepository monitors;
    private final CheckRepository checks;

    public ApiRoutes(MonitorRepository monitors, CheckRepository checks) {
        this.monitors = monitors;
        this.checks = checks;
    }

    public void register(JavalinConfig config) {
        config.routes.get("/api/monitors", this::listMonitors);
        config.routes.get("/api/monitors/{id}/checks", this::listChecks);
    }

    private void listMonitors(Context ctx) {
        ctx.json(monitors.list());
    }

    private void listChecks(Context ctx) {
        var id = ctx.pathParamAsClass("id", Integer.class).get();
        if (!monitors.exists(id)) {
            ctx.status(404);
            return;
        }
        ctx.json(checks.listForMonitor(id, MAX_CHECKS));
    }
}
