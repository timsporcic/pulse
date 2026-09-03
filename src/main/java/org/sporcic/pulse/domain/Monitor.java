package org.sporcic.pulse.domain;

public record Monitor(
        int id,
        String name,
        String url,
        int intervalSecs,
        boolean enabled,
        String notifyUrl
) {}
