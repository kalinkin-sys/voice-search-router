package local.voicerouter;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

final class UpdateScheduler {
    private static final int JOB_ID = 0x56535218;
    private static final long FLEX_MS = 6L * 60L * 60L * 1000L;

    private UpdateScheduler() {}

    static void sync(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(
                Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        if (!UpdateChecker.automatic(context)) {
            scheduler.cancel(JOB_ID);
            return;
        }
        if (scheduler.getPendingJob(JOB_ID) != null) return;

        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(UpdateChecker.CHECK_INTERVAL_MS, FLEX_MS)
                .build();
        scheduler.schedule(job);
    }
}
