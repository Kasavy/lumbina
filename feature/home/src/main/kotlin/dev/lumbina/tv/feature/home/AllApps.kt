package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.mostUsed
import dev.lumbina.tv.core.domain.home.AppsOrder
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.NamesPlace
import dev.lumbina.tv.core.domain.home.TileNames
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.NameDialog
import dev.lumbina.tv.core.ui.TileName
import dev.lumbina.tv.core.ui.anchorWhenFocused

internal enum class AppFilter { All, Tv, Phone, Hidden }

internal enum class AppsDialog { Find, Order, Show }

/** What the user asked "All apps" to show: a name search, a filter, and the dialog open over it. */
@Stable
internal class AppsChoice(filter: AppFilter = AppFilter.All, query: String = "") {
    var filter by mutableStateOf(filter)
    var query by mutableStateOf(query)
    var dialog by mutableStateOf<AppsDialog?>(null)

    /** Whether a search or a filter leaves some apps out. */
    val narrowed: Boolean get() = filter != AppFilter.All || query.isNotBlank()

    fun showAll() {
        filter = AppFilter.All
        query = ""
    }

    companion object {
        val Saver: Saver<AppsChoice, Any> = listSaver(
            save = { listOf(it.filter.name, it.query) },
            restore = { AppsChoice(AppFilter.valueOf(it[0]), it[1]) },
        )
    }
}

/**
 * The "All apps" screen as it shows now: [top] the most opened (only when sorting by use), then
 * [rest] by name, after the search and the [filter].
 */
internal data class AppsSection(
    val top: List<App>,
    val rest: List<App>,
    val filter: AppFilter,
    val query: String,
    val order: AppsOrder,
    /** False while the order can't be by use: a search, the hidden apps, nothing opened yet. */
    val orderApplies: Boolean,
    val hidden: Int,
    val size: TileSize,
    val name: TileName,
) {
    val count: Int get() = top.size + rest.size

    /** The first tile, where the focus goes when the section is opened or changed. */
    val firstKey: FocusKey? get() = (top.firstOrNull() ?: rest.firstOrNull())?.let { FocusKey(ROW, it.id.flatten()) }

    companion object {
        val ROW = HomeRow.AllApps.key

        /** The screen's heading in its grid. */
        val TITLE = "title:$ROW"
        val SHOW_ALL = FocusKey(ROW, "show-all")

        /** The search button, where the focus goes on a screen with no apps to show. */
        val FIND = FocusKey(ROW, "find")
    }
}

/** The section for [state] and what the user [chose]; null until the apps are read. */
@Composable
internal fun rememberAppsSection(state: HomeUiState?, chose: AppsChoice): AppsSection? {
    val content = state?.content ?: return null
    val look = state.appearance
    // Once nothing is hidden any more, the hidden filter is gone: show everything.
    val filter = if (chose.filter == AppFilter.Hidden && content.hidden.isEmpty()) AppFilter.All else chose.filter
    // Kept that way: apps hidden later must not bring the filter back.
    if (filter != chose.filter) SideEffect { chose.filter = filter }
    val words = chose.query.trim()
    // Not on every change of the look or the layout: a tile being moved or a size being set
    // would sort every app again on each step.
    val size = look.tileSizeOf(AppsSection.ROW)
    val names = look.namesIn(AppsSection.ROW) to look.namesPlaceIn(AppsSection.ROW)
    return remember(content.apps, content.hidden, filter, words, look.appsOrder, state.usage, size, names) {
        appsSection(state, filter, words)
    }
}

private fun appsSection(state: HomeUiState, filter: AppFilter, words: String): AppsSection {
    val look = state.appearance
    val hiddenOnes = filter == AppFilter.Hidden
    val orderApplies = !hiddenOnes && words.isEmpty() && state.usage.isNotEmpty()
    val shown = (if (hiddenOnes) state.content.hidden else state.content.apps)
        .filter { filter.shows(it) && it.label.contains(words, ignoreCase = true) }
        .sortedWith(appsByName())
    val byUse = orderApplies && look.appsOrder == AppsOrder.ByUse
    val top = if (byUse) mostUsed(shown, state.usage, TOP_APPS) else emptyList()
    return AppsSection(
        top = top,
        rest = shown - top.toSet(),
        filter = filter,
        query = words,
        order = look.appsOrder,
        orderApplies = orderApplies,
        hidden = state.content.hidden.size,
        size = look.tileSizeOf(AppsSection.ROW),
        // Hidden apps are looked for by name: there the names are always on.
        name = if (hiddenOnes) {
            TileName(TileNames.All, NamesPlace.Under)
        } else {
            TileName(look.namesIn(AppsSection.ROW), look.namesPlaceIn(AppsSection.ROW))
        },
    )
}

/**
 * The first apps of the "All apps" screen, in its order and with nothing filtered: what the "Apps"
 * row of home shows before its tile that opens the screen.
 */
internal fun homeApps(state: HomeUiState): List<App> {
    val byName = state.content.apps.sortedWith(appsByName())
    val byUse = state.appearance.appsOrder == AppsOrder.ByUse && state.usage.isNotEmpty()
    val top = if (byUse) mostUsed(byName, state.usage, TOP_APPS) else emptyList()
    return (top + (byName - top.toSet())).take(HomeRow.AllApps.APPS)
}

private fun AppFilter.shows(app: App): Boolean = when (this) {
    AppFilter.Tv -> app.isTvApp
    AppFilter.Phone -> !app.isTvApp
    AppFilter.All, AppFilter.Hidden -> true
}

/**
 * After the search, the filter or the order changes, the focus goes to the section's first tile:
 * the button it was on stays, but what was under it is another list.
 */
@Composable
internal fun AppsFocusOnChange(section: AppsSection?, focus: HomeFocus) {
    val changed = section?.let { Triple(it.filter, it.query, it.order) }
    var seen by remember { mutableStateOf(changed) }
    LaunchedEffect(changed) {
        val before = seen
        seen = changed
        if (section == null || before == null || before == changed) return@LaunchedEffect
        focus.focusSoon(section.firstKey ?: AppsSection.SHOW_ALL)
    }
}

/** Every app, as the "All apps" screen lays them out: the heading with its three buttons, then the tiles. */
internal fun LazyGridScope.allApps(section: AppsSection, chose: AppsChoice, actions: HomeActions) {
    fullWidth(AppsSection.TITLE) { AppsHeader(section, onOpen = { chose.dialog = it }) }
    val span = GridItemSpan(section.size.gridSpan)
    val tiles: (List<App>) -> Unit = { apps ->
        items(apps, key = { "${AppsSection.ROW}:${it.id.flatten()}" }, span = { span }, contentType = { APP }) { app ->
            AppCard(
                app = app,
                onClick = { actions.open(app) },
                onLongClick = { actions.appMenu(app, Place.All) },
                name = section.name,
                banner = actions.banner(app),
                modifier = Modifier
                    .gridCell()
                    .anchorWhenFocused(actions.anchor)
                    .homeFocus(LocalHomeFocus.current, FocusKey(AppsSection.ROW, app.id.flatten())),
            )
        }
    }
    if (section.top.isNotEmpty()) {
        fullWidth("label:top") { AppsLabel(stringResource(R.string.apps_most_used)) }
        tiles(section.top)
        if (section.rest.isNotEmpty()) fullWidth("label:rest") { AppsLabel(stringResource(R.string.apps_the_rest)) }
    }
    tiles(section.rest)
    if (section.count == 0) fullWidth("empty:${AppsSection.ROW}") { NoApps(section, onShowAll = chose::showAll) }
}

@Composable
private fun AppsLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Why the section is empty, and the way out: a search or a filter left nothing. */
@Composable
private fun NoApps(section: AppsSection, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    val why = when {
        section.query.isNotEmpty() -> stringResource(R.string.apps_none_found, section.query)
        section.filter == AppFilter.Tv -> stringResource(R.string.apps_none_tv)
        section.filter == AppFilter.Phone -> stringResource(R.string.apps_none_phone)
        else -> stringResource(R.string.apps_nothing)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.padding(vertical = 8.dp)) {
        Text(why, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (section.query.isNotEmpty() || section.filter != AppFilter.All) {
            Button(
                onClick = onShowAll,
                modifier = Modifier.homeFocus(LocalHomeFocus.current, AppsSection.SHOW_ALL),
            ) { Text(stringResource(R.string.apps_show_all)) }
        }
    }
}

/** The dialog [chose] has open: the name to find, the order, what to show. The current choice is marked. */
@Composable
internal fun AppsDialogs(section: AppsSection, chose: AppsChoice, onOrder: (AppsOrder) -> Unit) {
    val close = { chose.dialog = null }
    when (chose.dialog) {
        AppsDialog.Find -> NameDialog(
            title = stringResource(R.string.apps_find_title),
            initial = chose.query,
            saveLabel = stringResource(R.string.apps_find),
            cancelLabel = stringResource(R.string.name_cancel),
            onSave = {
                chose.query = it.trim()
                close()
            },
            onDismiss = close,
        )

        AppsDialog.Order -> ChoiceDialog(
            title = stringResource(R.string.apps_order),
            choices = AppsOrder.entries.map { entry ->
                Choice(stringResource(entry.title), chosen = entry == section.order) { onOrder(entry) }
            },
            onDismiss = close,
        )

        AppsDialog.Show -> ChoiceDialog(
            title = stringResource(R.string.apps_show),
            choices = AppFilter.entries
                .filter { it != AppFilter.Hidden || section.hidden > 0 }
                .map { entry ->
                    val title = filterTitle(entry, section.hidden)
                    Choice(title, chosen = entry == section.filter) { chose.filter = entry }
                },
            onDismiss = close,
        )

        null -> Unit
    }
}

/**
 * "All apps", how many are shown, and three buttons, the most used first: find, the order, what
 * to show (the rarer filters live in there).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppsHeader(section: AppsSection, onOpen: (AppsDialog) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalHomeFocus.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Text(stringResource(R.string.home_all_apps), style = MaterialTheme.typography.titleMedium)
        Text(
            section.count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        FilterChip(
            selected = section.query.isNotEmpty(),
            onClick = { onOpen(AppsDialog.Find) },
            modifier = Modifier.homeFocus(focus, AppsSection.FIND),
        ) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(section.query.ifEmpty { stringResource(R.string.apps_find) }, maxLines = 1)
        }
        val byUse = section.orderApplies && section.order == AppsOrder.ByUse
        FilterChip(
            selected = false,
            enabled = section.orderApplies,
            onClick = { onOpen(AppsDialog.Order) },
            modifier = Modifier.homeFocus(focus, FocusKey(AppsSection.ROW, "order")),
        ) {
            Text(
                stringResource(if (byUse) R.string.apps_order_chip_by_use else R.string.apps_order_chip_by_name),
                maxLines = 1,
            )
        }
        FilterChip(
            selected = section.filter != AppFilter.All,
            onClick = { onOpen(AppsDialog.Show) },
            modifier = Modifier.homeFocus(focus, FocusKey(AppsSection.ROW, "show")),
        ) { Text(stringResource(R.string.apps_show_chip, filterTitle(section.filter, section.hidden)), maxLines = 1) }
    }
}

@Composable
private fun filterTitle(filter: AppFilter, hidden: Int): String = when (filter) {
    AppFilter.All -> stringResource(R.string.apps_filter_all)
    AppFilter.Tv -> stringResource(R.string.apps_filter_tv)
    AppFilter.Phone -> stringResource(R.string.apps_filter_phone)
    AppFilter.Hidden -> stringResource(R.string.apps_filter_hidden, hidden)
}

private val AppsOrder.title: Int
    get() = when (this) {
        AppsOrder.ByUse -> R.string.apps_order_by_use
        AppsOrder.ByName -> R.string.apps_order_by_name
    }

/** How many of the most used apps come first when sorting by use. */
private const val TOP_APPS = 5

private const val APP = "app"
