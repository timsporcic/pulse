package org.sporcic.pulse.jobs;

import org.jobrunr.configuration.JobRunr;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.JobActivator;
import org.jobrunr.storage.sql.sqlite.SqLiteStorageProvider;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.sporcic.pulse.check.Pinger;
import org.sporcic.pulse.data.CheckRepository;
import org.sporcic.pulse.data.Database;
import org.sporcic.pulse.data.MonitorRepository;
import org.sporcic.pulse.notify.WebhookNotifier;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wires JobRunr: job state lives in the same SQLite file as the app data
 * (extra jobrunr_* tables), no broker involved. The recurring job pings
 * whatever is due; an UP->DOWN transition enqueues a one-off notify job.
 */
public final class Jobs {

    private Jobs() {}

    public static void start(Path dbFile, io.micrometer.core.instrument.MeterRegistry registry) {
        var dataSource = Database.dataSource(dbFile);
        var dsl = DSL.using(dataSource, SQLDialect.SQLITE);
        var monitors = new MonitorRepository(dsl);
        var checks = new CheckRepository(dsl);

        var notifyJob = new NotifyDownJob(monitors, checks, new WebhookNotifier());

        // the scheduler only exists after initialize(); the listener runs later
        var scheduler = new AtomicReference<JobScheduler>();
        var checkJob = new CheckDueMonitorsJob(monitors, checks, new Pinger(),
                monitorId -> scheduler.get()
                        .enqueue((IocJobLambda<NotifyDownJob>) x -> x.notifyDown(monitorId)),
                new org.sporcic.pulse.metrics.CheckMetrics(registry));

        var jobScheduler = JobRunr.configure()
                .useStorageProvider(new SqLiteStorageProvider(dataSource))
                .useJobActivator(new JobActivator() {
                    @Override
                    public <T> T activateJob(Class<T> type) {
                        if (type == CheckDueMonitorsJob.class) {
                            return type.cast(checkJob);
                        }
                        if (type == NotifyDownJob.class) {
                            return type.cast(notifyJob);
                        }
                        throw new IllegalArgumentException("unknown job type: " + type);
                    }
                })
                .useBackgroundJobServer()
                .initialize()
                .getJobScheduler();
        scheduler.set(jobScheduler);

        jobScheduler.scheduleRecurrently("check-due-monitors", Duration.ofSeconds(15),
                (IocJobLambda<CheckDueMonitorsJob>) CheckDueMonitorsJob::run);
    }
}
