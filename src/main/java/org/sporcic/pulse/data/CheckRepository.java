package org.sporcic.pulse.data;

import org.jooq.DSLContext;
import org.sporcic.pulse.domain.Check;

import java.util.List;

import static org.sporcic.pulse.jooq.Tables.CHECK_RESULT;

public class CheckRepository {

    private final DSLContext dsl;

    public CheckRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /** Most recent checks first. */
    public List<Check> listForMonitor(int monitorId, int limit) {
        return dsl.selectFrom(CHECK_RESULT)
                .where(CHECK_RESULT.MONITOR_ID.eq(monitorId))
                .orderBy(CHECK_RESULT.ID.desc())
                .limit(limit)
                .fetch(r -> new Check(
                        r.getId(),
                        r.getMonitorId(),
                        r.getCheckedAt(),
                        r.getUp() != 0,
                        r.getStatusCode(),
                        r.getLatencyMs()
                ));
    }
}
