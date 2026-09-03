package org.sporcic.pulse.data;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.sporcic.pulse.domain.Monitor;
import org.sporcic.pulse.domain.MonitorView;
import org.sporcic.pulse.jooq.tables.records.MonitorRecord;

import java.math.BigDecimal;
import java.util.List;

import static org.sporcic.pulse.jooq.Tables.CHECK_RESULT;
import static org.sporcic.pulse.jooq.Tables.MONITOR;

public class MonitorRepository {

    private final DSLContext dsl;

    public MonitorRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

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

    public List<Monitor> list() {
        return dsl.selectFrom(MONITOR)
                .orderBy(MONITOR.ID)
                .fetch(MonitorRepository::toMonitor);
    }

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

    public boolean delete(int id) {
        return dsl.deleteFrom(MONITOR)
                .where(MONITOR.ID.eq(id))
                .execute() > 0;
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
