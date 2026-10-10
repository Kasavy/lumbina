package dev.lumbina.tv.vendor.generic

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.remote.isStandInHome
import javax.inject.Inject

/** An activity that declared itself a home screen. */
internal data class HomeCandidate(val id: AppId, val isSystemApp: Boolean)

/**
 * The device's own launcher: a system home activity other than Lumbina and the stand-ins
 * for a home screen ([isStandInHome]).
 * On Google TV this is `com.google.android.apps.tv.launcherx`.
 */
internal fun pickStockLauncher(candidates: List<HomeCandidate>, ownPackage: String): AppId? = candidates
    .filter { it.isSystemApp }
    .filterNot { it.id.packageName == ownPackage }
    .filterNot { it.id.isStandInHome() }
    .firstOrNull()
    ?.id

internal class StockLauncherFinder @Inject constructor(@ApplicationContext private val context: Context) {

    fun find(): AppId? {
        val homes = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            0,
        ).map {
            HomeCandidate(
                id = AppId(it.activityInfo.packageName, it.activityInfo.name),
                isSystemApp = it.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            )
        }
        return pickStockLauncher(homes, context.packageName)
    }
}
