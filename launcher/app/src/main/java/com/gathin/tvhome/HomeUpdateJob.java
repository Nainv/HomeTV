package com.gathin.tvhome;

import android.app.job.JobParameters;
import android.app.job.JobService;
import java.util.concurrent.Future;

/** Periodic network-constrained update check. Android chooses its actual execution time. */
public final class HomeUpdateJob extends JobService {
    private Future<?> work;
    private volatile boolean stopped;
    @Override public boolean onStartJob(JobParameters params) {
        stopped = false;
        work = PersonalUpdates.checkBackground(this, () -> {
            if (!stopped) jobFinished(params, false);
        });
        return work != null;
    }
    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        if (work != null) work.cancel(true);
        return true;
    }
}
