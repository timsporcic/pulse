package org.sporcic.pulse.check;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PingerTest {

    Javalin target;
    Pinger pinger = new Pinger();

    @BeforeEach
    void startTarget() {
        target = Javalin.create(config -> {
            config.routes.get("/ok", ctx -> ctx.result("fine"));
            config.routes.get("/broken", ctx -> ctx.status(500));
        }).start(0);
    }

    @AfterEach
    void stopTarget() {
        target.stop();
    }

    String targetUrl(String path) {
        return "http://localhost:" + target.port() + path;
    }

    @Test
    void healthyUrlIsUpWithLatency() {
        var result = pinger.ping(targetUrl("/ok"));

        assertTrue(result.up());
        assertEquals(200, result.statusCode());
        assertNotNull(result.latencyMs());
        assertTrue(result.latencyMs() >= 0);
    }

    @Test
    void serverErrorIsDownWithStatusCode() {
        var result = pinger.ping(targetUrl("/broken"));

        assertFalse(result.up());
        assertEquals(500, result.statusCode());
    }

    @Test
    void unreachableHostIsDownWithoutStatusCode() {
        // nothing listens on this port
        var result = pinger.ping("http://localhost:59999/");

        assertFalse(result.up());
        assertNull(result.statusCode());
    }

    @Test
    void unsupportedUrlIsDownWithoutThrowing() {
        var result = pinger.ping("ftp://example.org");
        assertFalse(result.up());
        assertNull(result.statusCode());
    }

}
