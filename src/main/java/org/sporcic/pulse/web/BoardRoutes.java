package org.sporcic.pulse.web;

import io.javalin.config.JavalinConfig;
import io.javalin.http.Context;
import org.sporcic.pulse.data.MonitorRepository;

import java.util.Map;

public class BoardRoutes {

    private final MonitorRepository repository;

    public BoardRoutes(MonitorRepository repository) {
        this.repository = repository;
    }

    public void register(JavalinConfig config) {
        config.routes.get("/", this::page);
        config.routes.get("/board", this::fragment);
    }

    private void page(Context ctx) {
        ctx.render("board.jte", Map.of("monitors", repository.listViews()));
    }

    private void fragment(Context ctx) {
        ctx.render("fragments/rows.jte", Map.of("monitors", repository.listViews()));
    }
}
