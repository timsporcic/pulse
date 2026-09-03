package org.sporcic.pulse.data;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.sporcic.pulse.domain.Monitor;
import org.sporcic.pulse.domain.MonitorView;
import org.sporcic.pulse.jooq.tables.records.MonitorRecord;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Objects;
import java.time.Instant;
import java.util.List;

import static org.sporcic.pulse.jooq.Tables.CHECK_RESULT;
import static org.sporcic.pulse.jooq.Tables.MONITOR;

/**
 * Typed jOOQ access to the {@code monitor} table, plus the two derived reads
 * the rest of the app is built on: the board view ({@link #listViews()}) and
 * the checker's work list ({@link #listDue(Instant)}).
 */
public class MonitorRepository {

    private final DSLContext dsl;

    public MonitorRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Inserts an enabled monitor and returns it with its generated id.
     *
     * @param notifyUrl webhook for DOWN transitions; null means never notify
     */
    public Monitor add(String name, String url, int intervalSecs, String notifyUrl) {
        var record = dsl.insertInto(MONITOR)
                .set(MONITOR.NAME, name)
                .set(MONITOR.URL, url)
                .set(MONITOR.INTERVAL_SECS, intervalSecs)
                .set(MONITOR.ENABLED, 1)
                .set(MONITOR.NOTIFY_URL, notifyUrl)
                .returning()
                .fetchSingle();
        return toMonitor(record);
    }

    /** All monitors, enabled or not, ordered by id. */
    public List<Monitor> list() {
        return dsl.selectFrom(MONITOR)
                .orderBy(MONITOR.ID)
                .fetch(MonitorRepository::toMonitor);
    }

    /**
     * Every monitor joined with its latest check and lifetime uptime %, in one
     * query (correlated subselects, no N+1). Monitors that have never been
     * checked come back with null status fields - the board renders those as
     * "waiting for first check".
     */
    public List<MonitorView> listViews() {
        var lastUp = dsl.select(CHECK_RESULT.UP)
                .from(CHECK_RESULT)
                .where(CHECK_RESULT.MONITOR_ID.eq(MONITOR.ID))
                .orderBy(CHECK_RESULT.ID.desc())
                .limit(1)
                .asField("last_up");
        var lastLatency = dsl.select(CHECK_RESULT.LATENCY_MS)
                .from(CHECK_RESULT)
                .where(CHECK_RESULT.MONITOR_ID.eq(MONITOR.ID))
                .orderBy(CHECK_RESULT.ID.desc())
                .limit(1)
                .asField("last_latency");
        var uptimePct = dsl.select(DSL.avg(CHECK_RESULT.UP).mul(100))
                .from(CHECK_RESULT)
                .where(CHECK_RESULT.MONITOR_ID.eq(MONITOR.ID))
                .asField("uptime_pct");

        return dsl.select(MONITOR.ID, MONITOR.NAME, MONITOR.URL, lastUp, lastLatency, uptimePct)
                .from(MONITOR)
                .orderBy(MONITOR.ID)
                .fetch(r -> new MonitorView(
                        r.get(MONITOR.ID),
                        r.get(MONITOR.NAME),
                        r.get(MONITOR.URL),
                        r.get(lastUp) == null ? null : ((Number) r.get(lastUp)).intValue() != 0,
                        r.get(lastLatency) == null ? null : ((Number) r.get(lastLatency)).intValue(),
                        r.get(uptimePct) == null ? null : ((BigDecimal) r.get(uptimePct)).doubleValue()
                ));
    }

    /**
     * Enabled monitors whose last check is older than their interval, or that
     * have never been checked. This is the checker job's work list; a monitor
     * mid-interval is skipped so the 15-second job cadence never pings faster
     * than {@code interval_secs}. Due-ness is decided in Java after one query
     * (fine at this scale; checked_at is ISO-8601 UTC, which also compares
     * correctly as text if this ever moves into SQL).
     */
    public List<Monitor> listDue(Instant now) {
        var lastCheckedAt = dsl.select(DSL.max(CHECK_RESULT.CHECKED_AT))
                .from(CHECK_RESULT)
                .where(CHECK_RESULT.MONITOR_ID.eq(MONITOR.ID))
                .asField("last_checked_at");

        return dsl.select(MONITOR.fields())
                .select(lastCheckedAt)
                .from(MONITOR)
                .where(MONITOR.ENABLED.eq(1))
                .orderBy(MONITOR.ID)
                .fetch(r -> {
                    var monitor = toMonitor(r.into(MONITOR));
                    var last = (String) r.get(lastCheckedAt);
                    var due = last == null
                            || Instant.parse(last).plusSeconds(monitor.intervalSecs()).isBefore(now)
                            || Instant.parse(last).plusSeconds(monitor.intervalSecs()).equals(now);
                    return due ? monitor : null;
                })
                .stream().filter(Objects::nonNull).toList();
    }

    /** The monitor, or empty when the id is unknown (e.g. deleted since enqueue). */
    public Optional<Monitor> findById(int id) {
        return dsl.selectFrom(MONITOR)
                .where(MONITOR.ID.eq(id))
                .fetchOptional(MonitorRepository::toMonitor);
    }

    /** Cheap existence probe backing the JSON API's 404s. */
    public boolean exists(int id) {
        return dsl.fetchExists(dsl.selectFrom(MONITOR).where(MONITOR.ID.eq(id)));
    }

    /**
     * Deletes the monitor and its check history in one transaction. The
     * checks must go first: check_result has an enforced foreign key to
     * monitor with no cascade. Returns false when no such id existed, which
     * the web layer turns into a 404.
     */
    public boolean delete(int id) {
        return dsl.transactionResult(tx -> {
            tx.dsl().deleteFrom(CHECK_RESULT)
                    .where(CHECK_RESULT.MONITOR_ID.eq(id))
                    .execute();
            return tx.dsl().deleteFrom(MONITOR)
                    .where(MONITOR.ID.eq(id))
                    .execute() > 0;
        });
    }

    private static Monitor toMonitor(MonitorRecord record) {
        return new Monitor(
                record.getId(),
                record.getName(),
                record.getUrl(),
                record.getIntervalSecs(),
                record.getEnabled() != 0,
                record.getNotifyUrl()
        );
    }
}
