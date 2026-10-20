package dev.lumbina.tv.platform.packages

import android.content.Context
import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import dev.lumbina.tv.core.domain.update.UpdateStep
import dev.lumbina.tv.core.domain.update.isNewer
import java.io.File
import javax.inject.Inject

/** What a downloaded package is, as against this build of Lumbina. */
internal class PackageCheck @Inject constructor(
    @ApplicationContext private val context: Context,
    private val system: SystemInfo,
    private val log: LumbinaLog,
) {
    /** Whether [file] is a whole package of a later version than this one: an install that has not gone through. */
    fun isNewerRelease(file: File): Boolean = archive(file)?.versionName?.let { isNewer(it, system.appVersion) } == true

    /** Whether [file] is a whole package of [version]. */
    fun isVersion(file: File, version: String): Boolean = archive(file)?.versionName == version

    /**
     * Why [file] is not to be installed, or null when it is: it must be Lumbina's own package, no
     * older than this build. Over adb nothing else would stop another app from going in unasked.
     */
    fun unfit(file: File): UpdateStep? {
        val archive = archive(file)
        if (archive?.packageName != context.packageName) {
            log.log(UPDATE_TAG, "the package is ${archive?.packageName}, not Lumbina's")
            return UpdateStep.Corrupt
        }
        val here = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        val there = PackageInfoCompat.getLongVersionCode(archive)
        if (there < here) {
            log.log(UPDATE_TAG, "the package's version code $there is lower than this build's $here")
            return UpdateStep.Older
        }
        return null
    }

    private fun archive(file: File): PackageInfo? = context.packageManager.getPackageArchiveInfo(file.path, 0)
}
