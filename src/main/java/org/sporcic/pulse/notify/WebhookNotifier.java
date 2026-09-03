package org.sporcic.pulse.notify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Posts a JSON "monitor is down" payload to a webhook. Resilience is a plain
 * loop: three attempts with doubling backoff, then give up and log. No
 * circuit-breaker library - the JDK already does everything needed here.
 */
public class WebhookNotifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifier.class);
    private static final int MAX_ATTEMPTS = 3;

    /**
     * The JSON body the webhook receives. Field names are the external
     * contract; {@code status} is always {@code "down"} today but exists so
     * an up-recovery notification could reuse the shape.
     *
     * @param monitor    the monitor's display name
     * @param url        the monitored URL that went down
     * @param status     always {@code "down"} today
     * @param statusCode last HTTP status, or null when the site never answered
     * @param at         ISO-8601 UTC timestamp of the failing check
     */
    public record Payload(String monitor, String url, String status, Integer statusCode, String at) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final Duration baseBackoff;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Production configuration: 2s base backoff (attempts at +2s and +4s). */
    public WebhookNotifier() {
        this(Duration.ofSeconds(2));
    }

    /** @param baseBackoff first retry delay; doubles per attempt. Tests pass milliseconds. */
    public WebhookNotifier(Duration baseBackoff) {
        this.baseBackoff = baseBackoff;
    }

    /**
     * Posts a down notification: up to three attempts, backoff doubling from
     * {@code baseBackoff} between them. Success is any 2xx; non-2xx and
     * exceptions both retry. After the last attempt it logs and returns false
     * rather than throwing - deliberately, so JobRunr does not layer its own
     * ten retries on top of ours.
     *
     * @return true when the webhook accepted the notification
     */
    public boolean notifyDown(String notifyUrl, String monitorName, String monitorUrl, Integer statusCode, String at) {
        var request = HttpRequest.newBuilder(URI.create(notifyUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        toJson(new Payload(monitorName, monitorUrl, "down", statusCode, at))))
                .build();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() / 100 == 2) {
                    return true;
                }
                log.warn("webhook {} answered {} (attempt {}/{})", notifyUrl, response.statusCode(), attempt, MAX_ATTEMPTS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                log.warn("webhook {} failed: {} (attempt {}/{})", notifyUrl, e.getMessage(), attempt, MAX_ATTEMPTS);
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(baseBackoff.multipliedBy(1L << (attempt - 1)));
            }
        }
        log.error("giving up on webhook {} after {} attempts", notifyUrl, MAX_ATTEMPTS);
        return false;
    }

    private String toJson(Payload payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
