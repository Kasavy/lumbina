package dev.lumbina.tv.feature.home

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.home.HomeItem
import dev.lumbina.tv.core.domain.home.HomeSection
import java.util.Locale

/** The window's two groups: the apps the row shows, in its order, then the rest by name. */
internal data class PickGroups(val inRow: List<App>, val rest: List<App>) {
    val first: App? get() = inRow.firstOrNull() ?: rest.firstOrNull()
}

/**
 * [apps]: every app that can be picked; one of the section's that is not among them (not
 * installed, or hidden) is left out. The rest go by name alone in [locale], TV apps among the others.
 */
internal fun pickGroups(section: HomeSection, apps: List<App>, locale: Locale): PickGroups {
    val byId = apps.associateBy { it.id }
    val inRow = section.apps.mapNotNull(byId::get)
    val taken = inRow.mapTo(HashSet()) { it.id }
    return PickGroups(inRow, apps.filterNot { it.id in taken }.sortedWith(appsByName(locale)))
}

/** How many of [apps] (those the window shows) [section] has: one not installed or hidden is not counted. */
internal fun chosenCount(section: HomeSection, apps: List<App>): Int {
    val shown = apps.mapTo(HashSet()) { it.id }
    return section.apps.toSet().count { it in shown }
}

/** The name of the folder of [section] that holds [app]; null when the app stands in the row itself or not at all. */
internal fun folderOf(section: HomeSection, app: AppId): String? =
    section.items.firstNotNullOfOrNull { item -> (item as? HomeItem.Folder)?.takeIf { app in it.apps }?.name }

/** What the window's first tile is, where the focus starts. */
internal enum class PickStart { Shortcut, Inputs, Apps }

/**
 * The first tile: the one that opens all apps when it is in the row; with nothing in the row, the
 * inputs' group, which comes before the rest, or else that tile, which leads the rest; else the
 * first app in the row.
 */
internal fun pickStart(shortcutInRow: Boolean, rowEmpty: Boolean, hasInputs: Boolean): PickStart = when {
    shortcutInRow -> PickStart.Shortcut
    !rowEmpty -> PickStart.Apps
    hasInputs -> PickStart.Inputs
    else -> PickStart.Shortcut
}
