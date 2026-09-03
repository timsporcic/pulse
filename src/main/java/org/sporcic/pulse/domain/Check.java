package org.sporcic.pulse.domain;

/**
 * One ping outcome; mirrors one {@code check_result} row. Serialized directly
 * by the JSON API.
 *
 * @param checkedAt  ISO-8601 UTC
 * @param statusCode null when the site never answered (timeout, refused)
 * @param latencyMs  null when the site never answered
 * @param id         generated primary key, insertion-ordered
 * @param monitorId  owning monitor
 * @param up         whether the final status was below 400
 */
public record Check(
        int id,
        int monitorId,
        String checkedAt,
        boolean up,
        Integer statusCode,
        Integer latencyMs
) {}
