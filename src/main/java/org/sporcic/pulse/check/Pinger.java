package org.sporcic.pulse.check;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Pings a URL with a plain JDK HttpClient. Resilience is deliberately
 * hand-rolled: the timeouts here are the whole story - a slow or dead site
 * simply becomes a DOWN result.
 */
public class Pinger {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Outcome of a single ping. When no HTTP response was received (timeout,
     * refused connection, DNS failure, bad TLS), {@code statusCode} and
     * {@code latencyMs} are both null and {@code up} is false.
     *
     * @param up         final status below 400
     * @param statusCode HTTP status, or null when the site never answered
     * @param latencyMs  full-exchange wall time, or null when the site never answered
     */
    public record PingResult(boolean up, Integer statusCode, Integer latencyMs) {}

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * GETs the URL once, following redirects. Up means the final status is
     * below 400; latency covers the full exchange including redirects. Never
     * throws: any failure becomes a DOWN result. Blocking - callers run it on
     * a virtual thread.
     */
    public PingResult ping(String url) {
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        var started = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            var latencyMs = (int) ((System.nanoTime() - started) / 1_000_000);
            return new PingResult(response.statusCode() < 400, response.statusCode(), latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new PingResult(false, null, null);
        } catch (Exception e) {
            return new PingResult(false, null, null);
        }
    }
}
