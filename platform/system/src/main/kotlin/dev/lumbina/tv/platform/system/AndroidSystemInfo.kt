package dev.lumbina.tv.platform.system

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
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

    private fun memory() = ActivityManager.MemoryInfo().also {
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
    }
}
