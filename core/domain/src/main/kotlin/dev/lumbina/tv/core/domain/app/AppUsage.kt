package dev.lumbina.tv.core.domain.app

import kotlinx.coroutines.flow.Flow

/**
 * How often each app (by package name) was opened lately, so what people actually watch comes
 * first. Openings, not time on screen: an app left open overnight would win on time. Empty where
 * the device doesn't tell (e.g. usage access not granted).
 */
fun interface AppUsage {
    fun recent(): Flow<Map<String, Long>>
}

/**
 * Apps Lumbina itself opened (tiles, voice, the switcher): what [AppUsage] falls back to where the
 * device keeps its own statistics to itself, e.g. Google TV without adb.
 */
interface LaunchHistory {
    fun record(packageName: String)

    /** Openings per package over the last couple of weeks. */
    fun openings(): Flow<Map<String, Long>>
}

/**
 * The [max] most opened [apps] ([usage]: openings per package), leaving out [except] and apps
 * never opened; most opened first, ties in the given order.
 */
fun mostUsed(apps: List<App>, usage: Map<String, Long>, max: Int, except: Set<AppId> = emptySet()): List<App> = apps
    .filter { it.id !in except }
    .mapNotNull { app -> usage[app.id.packageName]?.takeIf { it > 0 }?.let { app to it } }
    .sortedByDescending { it.second }
    .take(max)
    .map { it.first }
