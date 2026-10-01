package local.voicerouter;

import android.app.job.JobService;
import android.app.job.JobParameters;

public final class UpdateJobService extends JobService {
    private volatile boolean stopped;

    @Override
    public boolean onStartJob(final JobParameters params) {
        stopped = false;
        new Thread(new Runnable() {
            @Override public void run() {
                UpdateChecker.Result result = UpdateChecker.check(
                        getApplicationContext());
                if (!stopped && result.update != null) {
                    UpdateNotifier.show(getApplicationContext(), result.update);
                }
                if (!stopped) jobFinished(params, false);
            }
        }, "update-check").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        return true;
    }
}
