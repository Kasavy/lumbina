package dev.lumbina.tv.platform.system

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PersistableBundle
import dagger.hilt.android.AndroidEntryPoint
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.health.HealAccessibilityService
import dev.lumbina.tv.core.domain.health.HealResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Re-enables the accessibility service when the system lists it but doesn't run it.
 *
 * The system may fail to bind it (some firmware holds third-party apps back after boot, and
 * nothing retries a refused bind) or stop it along the way. So besides a one-shot check after
 * boot/update, a persisted periodic job re-checks every 15 minutes (the platform minimum);
 * opening the Lumbina UI also triggers a check.
 */
@AndroidEntryPoint
class HealJob : JobService() {

    @Inject lateinit var heal: HealAccessibilityService

    @Inject lateinit var log: LumbinaLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val attempt = params.extras.getInt(EXTRA_ATTEMPT, 1)
        val kind = if (params.jobId == PERIODIC_JOB_ID) "periodic" else "check $attempt"
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = runCatching { heal() }
            ensureActive()
            log.log(TAG, "$kind: ${result.getOrElse { "failed: $it" }}")
            // After re-enabling, confirm the service actually came up.
            if (result.getOrNull() == HealResult.Reenabled && attempt < MAX_ATTEMPTS) {
                scheduleCheck(this@HealJob, attempt + 1)
            }
            running.remove(params.jobId)
            jobFinished(params, false)
        }
        running[params.jobId] = job
        job.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Schedules a check after boot and after an update of Lumbina itself. */
    @AndroidEntryPoint
    class Trigger : BroadcastReceiver() {
        @Inject lateinit var log: LumbinaLog

        override fun onReceive(context: Context, intent: Intent) {
            ensureScheduled(context)
            if (context.getSystemService(JobScheduler::class.java).getPendingJob(PERIODIC_JOB_ID) == null) return
            log.log(TAG, "scheduling check after ${intent.action}")
            scheduleCheck(context, attempt = 1)
        }
    }

    companion object {
        private const val TAG = "heal"
        private const val CHECK_JOB_ID = 0x4c55
        private const val PERIODIC_JOB_ID = 0x4c56
        private const val EXTRA_ATTEMPT = "attempt"
        private const val MAX_ATTEMPTS = 3
        private const val CHECK_DELAY_MILLIS = 30_000L
        private const val PERIOD_MILLIS = 15 * 60_000L

        /** One-shot check shortly from now. Persisted, so it survives a reboot. */
        fun scheduleCheck(context: Context, attempt: Int, delayMillis: Long = CHECK_DELAY_MILLIS) {
            val extras = PersistableBundle().apply { putInt(EXTRA_ATTEMPT, attempt) }
            val job = JobInfo.Builder(CHECK_JOB_ID, ComponentName(context, HealJob::class.java))
                .setMinimumLatency(delayMillis)
                .setOverrideDeadline(delayMillis * 2)
                .setPersisted(true)
                .setExtras(extras)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(job)
        }

        /**
         * Keeps the periodic check in sync with the user's choice: it exists only while
         * WRITE_SECURE_SETTINGS is granted (revoking it over adb turns self-healing off).
         * Cheap to call on every app start.
         */
        fun ensureScheduled(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                scheduler.cancel(PERIODIC_JOB_ID)
                scheduler.cancel(CHECK_JOB_ID)
                return
            }
            if (scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return
            val job = JobInfo.Builder(PERIODIC_JOB_ID, ComponentName(context, HealJob::class.java))
                .setPeriodic(PERIOD_MILLIS)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
        }
    }
}
