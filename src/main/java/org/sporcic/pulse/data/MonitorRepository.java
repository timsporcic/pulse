package org.sporcic.pulse.data;

import org.jooq.DSLContext;
import org.sporcic.pulse.domain.Monitor;
import org.sporcic.pulse.jooq.tables.records.MonitorRecord;

import java.util.List;

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
