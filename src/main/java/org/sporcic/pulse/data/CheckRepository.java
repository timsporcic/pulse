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

    public void add(int monitorId, String checkedAt, boolean up, Integer statusCode, Integer latencyMs) {
        dsl.insertInto(CHECK_RESULT)
                .set(CHECK_RESULT.MONITOR_ID, monitorId)
                .set(CHECK_RESULT.CHECKED_AT, checkedAt)
                .set(CHECK_RESULT.UP, up ? 1 : 0)
                .set(CHECK_RESULT.STATUS_CODE, statusCode)
                .set(CHECK_RESULT.LATENCY_MS, latencyMs)
                .execute();
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
