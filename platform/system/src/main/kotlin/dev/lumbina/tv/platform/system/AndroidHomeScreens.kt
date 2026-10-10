package dev.lumbina.tv.platform.system

import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.domain.remote.HomeScreens
import dev.lumbina.tv.core.domain.remote.isStandInHome
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An activity that declared itself a home screen, with its own label and its app's. */
internal data class HomeActivity(val id: AppId, val label: String, val appLabel: String)

/**
 * The home screens to offer among [activities]: not Lumbina's own ([ownPackage]) and not the
 * stand-ins. The [firmware]'s launcher comes first under [firmwareName], as its own label is often
 * not one to read; screens of one name are told apart by what differs: the app's name, else the
 * package, else the activity.
 */
internal fun homeScreens(
    activities: List<HomeActivity>,
    ownPackage: String,
    firmware: AppId?,
    firmwareName: String,
): List<HomeScreen> {
    val named = activities
        .filterNot { it.id.packageName == ownPackage || it.id.isStandInHome() }
        .map { if (it.id == firmware) it.copy(label = firmwareName) else it }
    return named
        .map { screen ->
            val twins = named.filter { it.label == screen.label }
            HomeScreen(screen.id, if (twins.size > 1) "${screen.label} (${screen.apartFrom(twins)})" else screen.label)
        }
        .sortedByDescending { it.id == firmware }
}

private fun HomeActivity.apartFrom(twins: List<HomeActivity>): String = when (twins.size) {
    twins.distinctBy { it.appLabel }.size -> appLabel
    twins.distinctBy { it.id.packageName }.size -> id.packageName
    else -> id.activityName.substringAfterLast('.')
}

/** The activities that declared themselves a home screen, as the system lists them ([homeScreens]). */
internal class AndroidHomeScreens @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profile: DeviceProfile,
) : HomeScreens {

    override suspend fun installed(): List<HomeScreen> = withContext(Dispatchers.IO) {
        val packages = context.packageManager
        val activities = packages.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo }
            .map {
                HomeActivity(
                    AppId(it.packageName, it.name),
                    it.loadLabel(packages).toString(),
                    it.applicationInfo.loadLabel(packages).toString(),
                )
            }
        val brand = Build.BRAND.replaceFirstChar { it.titlecase() }
        val firmwareName = context.getString(R.string.home_firmware_launcher, brand)
        homeScreens(activities, context.packageName, profile.stockLauncher, firmwareName)
    }
}
