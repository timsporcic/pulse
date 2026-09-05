package org.sporcic.pulse.web;

import io.javalin.config.JavalinConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.domain.Monitor;
import org.sporcic.pulse.metrics.CheckMetrics;

import java.net.URI;

/**
 * Form-encoded mutation endpoints the board UI posts to via htmx:
 * {@code POST /monitors} (201 with the new id as plain text, 400 when name or
 * url is missing) and {@code DELETE /monitors/{id}} (204, or 404 for an
 * unknown id). Reads live in the JSON API under {@code /api}.
 */
public class MonitorRoutes {

    private final MonitorRepository repository;
    private final CheckMetrics metrics;

    public MonitorRoutes(MonitorRepository repository, CheckMetrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    public void register(JavalinConfig config) {
        config.routes.post("/monitors", this::add);
        config.routes.delete("/monitors/{id}", this::delete);
    }

    private void add(Context ctx) {
        var name = ctx.formParam("name");
        var url = ctx.formParam("url");
        if (name == null || name.isBlank() || url == null || url.isBlank()) {
            ctx.status(400).result("name and url are required");
            return;
        }
        var intervalSecs = ctx.formParamAsClass("interval_secs", Integer.class).getOrDefault(60);
        if (intervalSecs <= 0) {
            throw new BadRequestResponse("interval_secs must be positive");
        }
        url = httpUrl(url, "url");
        var notifyUrl = ctx.formParam("notify_url");
        notifyUrl = notifyUrl == null || notifyUrl.isBlank() ? null : httpUrl(notifyUrl, "notify_url");

        Monitor monitor = repository.add(name, url, intervalSecs, notifyUrl);
        ctx.header("HX-Trigger", "monitorsChanged").status(201).result(String.valueOf(monitor.id()));
    }

    private static String httpUrl(String value, String field) {
        try {
            var uri = URI.create(value.strip());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(field + " must be an HTTP or HTTPS URL with a host and no embedded credentials");
        }
    }

    private void delete(Context ctx) {
        var id = ctx.pathParamAsClass("id", Integer.class).get();
        if (repository.delete(id)) {
            metrics.remove(id);
            ctx.header("HX-Trigger", "monitorsChanged").status(204);
        } else {
            ctx.status(404);
        }
    }
}
