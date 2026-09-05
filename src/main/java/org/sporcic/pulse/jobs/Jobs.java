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
import org.sporcic.pulse.metrics.CheckMetrics;
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

    /**
     * Configures and starts the JobRunr background server, then registers the
     * recurring check job. Call exactly once, at boot, before the web server -
     * JobRunr keeps static state and a thread pool alive for the process
     * lifetime. Opens its own DataSource on the same SQLite file as the web
     * app; the IMMEDIATE transaction mode in {@code Database} is what makes
     * those two writers coexist.
     *
     * <p>The JobActivator below is the whole "dependency injection" story:
     * JobRunr stores job class names in SQLite and asks the activator for the
     * live instance at execution time. Both job singletons are wired by hand
     * right here.
     */
    public static void start(Path dbFile, CheckMetrics metrics) {
        var dataSource = Database.dataSource(dbFile);
        var dsl = DSL.using(dataSource, SQLDialect.SQLITE);
        var monitors = new MonitorRepository(dsl);
        var checks = new CheckRepository(dsl);

        var notifyJob = new NotifyDownJob(monitors, checks, new WebhookNotifier());

        // the scheduler only exists after initialize(); the listener runs later
        var scheduler = new AtomicReference<JobScheduler>();
        var checkJob = new CheckDueMonitorsJob(monitors, checks, new Pinger(),
                (monitorId, checkId) -> scheduler.get()
                        .enqueue((IocJobLambda<NotifyDownJob>) x -> x.notifyDown(monitorId, checkId)),
                metrics);

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
