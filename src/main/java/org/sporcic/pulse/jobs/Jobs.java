package org.sporcic.pulse.jobs;

import org.jobrunr.configuration.JobRunr;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.server.JobActivator;
import org.jobrunr.storage.sql.sqlite.SqLiteStorageProvider;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.Database;
import org.sporcic.pulse.data.MonitorRepository;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Wires JobRunr: job state lives in the same SQLite file as the app data
 * (extra jobrunr_* tables), no broker involved. The single recurring job
 * pings whatever is due.
 */
public final class Jobs {

    private Jobs() {}

    public static void start(Path dbFile) {
        var dataSource = Database.dataSource(dbFile);
        var dsl = DSL.using(dataSource, SQLDialect.SQLITE);
        var job = new CheckDueMonitorsJob(
                new MonitorRepository(dsl),
                new CheckRepository(dsl),
                new Pinger());

        JobRunr.configure()
                .useStorageProvider(new SqLiteStorageProvider(dataSource))
                .useJobActivator(new JobActivator() {
                    @Override
                    public <T> T activateJob(Class<T> type) {
                        if (type == CheckDueMonitorsJob.class) {
                            return type.cast(job);
                        }
                        throw new IllegalArgumentException("unknown job type: " + type);
                    }
                })
                .useBackgroundJobServer()
                .initialize()
                .getJobScheduler()
                .scheduleRecurrently("check-due-monitors", Duration.ofSeconds(15),
                        (IocJobLambda<CheckDueMonitorsJob>) CheckDueMonitorsJob::run);
    }
}
