package org.sporcic.pulse.web;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.domain.Monitor;

import java.util.stream.Collectors;

/**
 * Plain-text/form endpoints for managing monitors. A proper JSON API with
 * record DTOs arrives in the 04-json stage.
 */
public class MonitorRoutes {

    private final MonitorRepository repository;

    public MonitorRoutes(MonitorRepository repository) {
        this.repository = repository;
    }

    public void register(JavalinConfig config) {
        config.routes.get("/monitors", this::list);
        config.routes.post("/monitors", this::add);
        config.routes.delete("/monitors/{id}", this::delete);
    }

    private void list(Context ctx) {
        ctx.result(repository.list().stream()
                .map(m -> m.id() + " " + m.name() + " " + m.url())
                .collect(Collectors.joining("\n")));
    }

    private void add(Context ctx) {
        var name = ctx.formParam("name");
        var url = ctx.formParam("url");
        if (name == null || name.isBlank() || url == null || url.isBlank()) {
            ctx.status(400).result("name and url are required");
            return;
        }
        var intervalSecs = ctx.formParamAsClass("interval_secs", Integer.class).getOrDefault(60);
        var notifyUrl = ctx.formParam("notify_url");

        Monitor monitor = repository.add(name, url, intervalSecs, notifyUrl);
        ctx.status(201).result(String.valueOf(monitor.id()));
    }

    private void delete(Context ctx) {
        var id = ctx.pathParamAsClass("id", Integer.class).get();
        if (repository.delete(id)) {
            ctx.status(204);
        } else {
            ctx.status(404);
        }
    }
}
