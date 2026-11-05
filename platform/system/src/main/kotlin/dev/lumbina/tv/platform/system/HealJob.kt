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
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
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
 * Re-enables the accessibility service when the system doesn't run it, listed or taken off the list.
 *
 * The system may fail to bind it (some firmware holds third-party apps back after boot, and
 * nothing retries a refused bind) or stop it along the way, and another app may take it off the
 * list. So besides a one-shot check after boot/update, a persisted periodic job re-checks every
 * 15 minutes (the platform minimum); opening the Lumbina UI also triggers a check, and so does any
 * start of Lumbina's process ([checkSoon]): Hisense never hands apps the boot broadcast (none came
 * in any boot logged on the projector).
 */
@AndroidEntryPoint
class HealJob : JobService() {

    @Inject lateinit var heal: HealAccessibilityService

    @Inject lateinit var log: LumbinaLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = ConcurrentHashMap<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val attempt = params.extras.getInt(EXTRA_ATTEMPT, 1)
        // The periodic one counts too: the process it starts then needs no check of its own.
        checked(this)
        val kind = if (params.jobId == PERIODIC_JOB_ID) "periodic" else "check $attempt"
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val result = runCatching { heal() }
            ensureActive()
            // "All is well" every 15 minutes says nothing: only what had to be done or went wrong.
            if (result.getOrNull() != HealResult.AlreadyRunning) {
                log.log(TAG, "$kind: ${result.getOrElse { "failed: $it" }}")
            }
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
            val healing = context.getSystemService(JobScheduler::class.java).getPendingJob(PERIODIC_JOB_ID) != null
            // Logged whether it schedules or not: that it came at all tells about the firmware.
            val scheduled = healing && scheduleUnlessComing(context)
            log.log(TAG, "${intent.action}: ${if (scheduled) "check scheduled" else "no check needed"}")
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
        private const val CHECK_GAP_MILLIS = 5 * 60_000L
        private const val PREFS = "heal"
        private const val LAST_CHECK = "lastCheck"
        private const val LAST_BOOT = "lastBoot"

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
         * A check shortly from now, for a start of Lumbina's process: where the boot broadcast never
         * comes (Hisense), a service taken off the list before or at boot comes back once something
         * starts Lumbina, not at the next periodic check up to 15 minutes later. Not while one is
         * coming already ([scheduleUnlessComing]), nor within [CHECK_GAP_MILLIS] of the last: a
         * process the firmware kills and starts again and again must not toggle the service each
         * time. A bound service leaves the check nothing to do.
         */
        fun checkSoon(context: Context) {
            if (!canHeal(context)) return
            val prefs = prefs(context)
            val last = LastCheck(prefs.getLong(LAST_CHECK, 0), prefs.getInt(LAST_BOOT, NO_BOOT))
            if (checkDue(last, LastCheck(SystemClock.elapsedRealtime(), bootCount(context)), CHECK_GAP_MILLIS)) {
                scheduleUnlessComing(context)
            }
        }

        /**
         * The first check of a run, unless one is coming already: a run of checks after a
         * re-enable keeps its count, whatever asks for another. True when it scheduled one.
         */
        private fun scheduleUnlessComing(context: Context): Boolean {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(CHECK_JOB_ID) != null) return false
            scheduleCheck(context, attempt = 1)
            return true
        }

        private fun checked(context: Context) {
            prefs(context).edit {
                putLong(LAST_CHECK, SystemClock.elapsedRealtime())
                putInt(LAST_BOOT, bootCount(context))
            }
        }

        /** Which boot this is, counted by Android; [NO_BOOT] where it won't say. */
        private fun bootCount(context: Context): Int =
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, NO_BOOT)

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun canHeal(context: Context) =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /**
         * Keeps the periodic check in sync with the user's choice: it exists only while
         * WRITE_SECURE_SETTINGS is granted (revoking it over adb turns self-healing off).
         * Cheap to call on every app start.
         */
        fun ensureScheduled(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!canHeal(context)) {
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

/** A check's time since boot ([at], 0 for none) and the boot it was in. */
internal data class LastCheck(val at: Long, val boot: Int)

/** Where the boot is not known (Android won't say). */
internal const val NO_BOOT = -1

/**
 * Whether a check is due [now], [last] being the last one: after none, in another boot (a time
 * since boot says nothing across a reboot: a check at a minute in the boot before and a start at
 * three minutes in this one would look two minutes apart), and once [gap] has passed. Where the
 * boot is not known, by the time alone.
 */
internal fun checkDue(last: LastCheck, now: LastCheck, gap: Long): Boolean {
    if (last.at == 0L) return true
    if (last.boot != NO_BOOT && now.boot != NO_BOOT && last.boot != now.boot) return true
    return now.at - last.at !in 0 until gap
}
