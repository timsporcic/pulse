package org.sporcic.pulse.domain;

public record Check(
        int id,
        int monitorId,
        String checkedAt,
        boolean up,
        Integer statusCode,
        Integer latencyMs
) {}
