package dev.lumbina.tv.platform.system

import android.Manifest
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.InputDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Read on each access, so the diagnostics screen shows current memory and storage. */
internal class AndroidSystemInfo @Inject constructor(@ApplicationContext private val context: Context) : SystemInfo {

    override val appVersion: String
        get() = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    override val debugBuild: Boolean
        get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    override val manufacturer: String get() = Build.MANUFACTURER.orEmpty()

    override val model: String get() = Build.MODEL.orEmpty()

    override val androidRelease: String get() = Build.VERSION.RELEASE.orEmpty()

    override val sdkInt: Int get() = Build.VERSION.SDK_INT

    override val totalRamBytes: Long get() = memory().totalMem

    override val availableRamBytes: Long get() = memory().availMem

    override val freeStorageBytes: Long get() = StatFs(Environment.getDataDirectory().path).availableBytes

    override val firmware: String get() = Build.DISPLAY.orEmpty()

    override val fingerprint: String get() = Build.FINGERPRINT.orEmpty()

    override val hardware: String
        get() {
            val chip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}"
            } else {
                null
            }
            return listOfNotNull(Build.BOARD, Build.HARDWARE, chip).filter { it.isNotBlank() }.distinct().joinToString()
        }

    override val uptimeMillis: Long get() = SystemClock.elapsedRealtime()

    override val inputDevices: List<String>
        get() = InputDevice.getDeviceIds().toList().mapNotNull(InputDevice::getDevice).filterNot { it.isVirtual }.map {
            String.format(Locale.US, "%s (%04x:%04x)", it.name, it.vendorId, it.productId)
        }

    override val homeApp: String?
        get() {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val opened = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            // With no home app chosen the system answers with its own chooser.
            return opened?.activityInfo?.packageName?.takeUnless { it == "android" }
        }

    override val microphoneGranted: Boolean
        get() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override val processes: List<String> get() = activities().runningAppProcesses.orEmpty().map { it.processName }

    override val processExits: List<String>
        get() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
            val time = SimpleDateFormat(EXIT_TIME, Locale.US)
            return activities().getHistoricalProcessExitReasons(context.packageName, 0, EXITS).map {
                val what = listOfNotNull(reasonName(it.reason), it.description).joinToString(": ")
                "${time.format(Date(it.timestamp))} ${it.processName}: $what"
            }
        }

    override fun packageVersion(packageName: String): String? = try {
        context.packageManager.getPackageInfo(packageName, 0).versionName ?: "installed"
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun activities() = context.getSystemService(ActivityManager::class.java)

    private fun memory() = ActivityManager.MemoryInfo().also { activities().getMemoryInfo(it) }

    private companion object {
        const val EXITS = 12
        const val EXIT_TIME = "MM-dd HH:mm:ss"

        /** [ApplicationExitInfo]'s reason in words; a number for one not listed. */
        @Suppress("CyclomaticComplexMethod") // one line per constant
        fun reasonName(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "exit self"
            ApplicationExitInfo.REASON_SIGNALED -> "signaled"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low memory"
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission change"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive resource usage"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "user requested"
            ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency died"
            ApplicationExitInfo.REASON_OTHER -> "other"
            else -> "reason $reason"
        }
    }
}
