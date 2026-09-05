package org.sporcic.pulse.data;

import org.jooq.DSLContext;
import org.sporcic.pulse.domain.Check;
import org.sporcic.pulse.jooq.tables.records.CheckResultRecord;

import java.util.List;
import java.util.Optional;

import static org.sporcic.pulse.jooq.Tables.CHECK_RESULT;

/**
 * Typed jOOQ access to the {@code check_result} table: the checker appends,
 * the JSON API reads. Rows are never updated or individually deleted;
 * history goes away only when its monitor does.
 */
public class CheckRepository {

    private final DSLContext dsl;

    public CheckRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Appends one check result and returns its ID for queued notifications.
     *
     * @param checkedAt ISO-8601 UTC timestamp; stored as text and compared
     *                  lexically, so the format must stay sortable
     * @param statusCode null when no HTTP response was received
     * @param latencyMs  null when no HTTP response was received
     */
    public int add(int monitorId, String checkedAt, boolean up, Integer statusCode, Integer latencyMs) {
        return dsl.insertInto(CHECK_RESULT)
                .set(CHECK_RESULT.MONITOR_ID, monitorId)
                .set(CHECK_RESULT.CHECKED_AT, checkedAt)
                .set(CHECK_RESULT.UP, up ? 1 : 0)
                .set(CHECK_RESULT.STATUS_CODE, statusCode)
                .set(CHECK_RESULT.LATENCY_MS, latencyMs)
                .returning(CHECK_RESULT.ID)
                .fetchSingle().getId();
    }

    public Optional<Check> findById(int id) {
        return dsl.selectFrom(CHECK_RESULT)
                .where(CHECK_RESULT.ID.eq(id))
                .fetchOptional(CheckRepository::toCheck);
    }

    private static Check toCheck(CheckResultRecord r) {
        return new Check(r.getId(), r.getMonitorId(), r.getCheckedAt(), r.getUp() != 0,
                r.getStatusCode(), r.getLatencyMs());
    }

    /**
     * The monitor's checks, most recent first. Ordered by id rather than
     * checked_at: ids are insertion-ordered and indexed for free, and the
     * checker is the only writer.
     */
    public List<Check> listForMonitor(int monitorId, int limit) {
        return dsl.selectFrom(CHECK_RESULT)
                .where(CHECK_RESULT.MONITOR_ID.eq(monitorId))
                .orderBy(CHECK_RESULT.ID.desc())
                .limit(limit)
                .fetch(CheckRepository::toCheck);
    }
}
