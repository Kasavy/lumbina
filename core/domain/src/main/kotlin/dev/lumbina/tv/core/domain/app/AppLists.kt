package dev.lumbina.tv.core.domain.app

/** Visible apps installed within [RECENT_DAYS], newest first, at most [RECENT_MAX]. */
fun recentlyInstalled(apps: List<App>, now: Long): List<App> = apps
    .filter { it.installedAt > 0 && now - it.installedAt < RECENT_DAYS * DAY_MILLIS }
    .sortedByDescending { it.installedAt }
    .take(RECENT_MAX)

/** [packages] (the switcher's order) as apps, one launcher entry each, hidden ones left out. */
fun recentlyOpened(apps: List<App>, packages: List<String>): List<App> {
    val byPackage = apps.groupBy { it.id.packageName }
    return packages.mapNotNull { byPackage[it]?.first() }
}

/** Each package's name as the user sees it, from its first launcher entry. */
fun labelsByPackage(apps: List<App>): Map<String, String> = buildMap {
    apps.forEach { putIfAbsent(it.id.packageName, it.label) }
}

private const val RECENT_DAYS = 14
private const val RECENT_MAX = 10
private const val DAY_MILLIS = 24 * 60 * 60_000L
