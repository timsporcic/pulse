package org.sporcic.pulse;

import io.javalin.testtools.JavalinTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    @Test
    void servesHelloPageAtRoot() {
        JavalinTest.test(App.create(), (server, client) -> {
            var response = client.get("/");
            assertEquals(200, response.code());
            assertTrue(response.body().string().contains("Pulse"));
        });
    }
}
