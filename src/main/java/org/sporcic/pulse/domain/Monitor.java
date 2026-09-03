package org.sporcic.pulse.domain;

/**
 * A registered site to watch; mirrors one {@code monitor} row. Serialized
 * directly by the JSON API - field names are the API contract.
 *
 * @param intervalSecs minimum seconds between checks (the recurring job may
 *                     check later than this, never sooner)
 * @param notifyUrl    webhook POSTed on an UP-to-DOWN transition; null or
 *                     blank means never notify
 * @param id           generated primary key
 * @param name         display name shown on the board
 * @param url          the URL that gets pinged
 * @param enabled      disabled monitors are never checked
 */
public record Monitor(
        int id,
        String name,
        String url,
        int intervalSecs,
        boolean enabled,
        String notifyUrl
) {}
