package dev.lumbina.tv.core.domain.app

import kotlinx.coroutines.flow.Flow

/**
 * A launchable app entry. [isTvApp] is false for phone apps that only declare a LAUNCHER activity.
 * [installedAt]: when it was first installed, [updatedAt]: when last updated (wall-clock millis;
 * 0 if unknown). An update may bring a new banner, so artwork is cached per [updatedAt].
 */
data class App(
    val id: AppId,
    val label: String,
    val isTvApp: Boolean,
    val installedAt: Long = 0,
    val updatedAt: Long = 0,
)

/** Launchable apps on the device; emits again whenever apps are installed, updated or removed. */
fun interface InstalledApps {
    fun observe(): Flow<List<App>>
}

fun interface AppLauncher {
    /** Returns false if the app could not be started (e.g. it was just uninstalled). */
    fun launch(id: AppId): Boolean
}

/** What the system does for an app beyond starting it. Each returns false if unavailable. */
interface AppSystemActions {
    /** Whether the device has an "App info" screen (some TV firmware leaves it out). */
    val hasDetails: Boolean

    /** Opens the system "App info" screen. */
    fun showDetails(packageName: String): Boolean

    /** Asks the system to uninstall the app (the user confirms). */
    fun uninstall(packageName: String): Boolean
}
