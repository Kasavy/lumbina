package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.mostUsed
import dev.lumbina.tv.core.domain.home.AppsOrder
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.AppCard
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.NameDialog
import dev.lumbina.tv.core.ui.TileAnchor
import dev.lumbina.tv.core.ui.anchorWhenFocused

private enum class AppFilter { All, Tv, Phone, Hidden }

private enum class AppsDialog { Find, Order, Show }

/** The apps in the order chosen: [top] the most opened (only when sorting by use), then [rest]. */
private data class OrderedApps(val top: List<App>, val rest: List<App>)

/**
 * Every app on its own screen, off the home screen: a name search, the order (most used first
 * or by name), a filter (TV / phone / hidden) and the same menu as on home (hidden apps can be
 * shown again from it). The apps are the home screen's own: [home] is where its view model lives.
 */
@Suppress("ViewModelForwarding") // the dialogs take it only as HomeCommands
@Composable
fun AllAppsRoute(
    home: ViewModelStoreOwner,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(home),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    var chosen by rememberSaveable { mutableStateOf(AppFilter.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<AppsDialog?>(null) }
    val menu = remember { MenuState() }
    // Once nothing is hidden any more, the hidden filter is gone: show everything.
    val filter = if (chosen == AppFilter.Hidden && current.content.hidden.isEmpty()) AppFilter.All else chosen
    val source = if (filter == AppFilter.Hidden) current.content.hidden else current.content.apps
    val order = current.appearance.appsOrder
    val apps = remember(source, filter, query, order, current.usage) {
        orderedApps(source, filter, query.trim(), order, current.usage)
    }
    AppsGrid(
        apps = apps,
        header = {
            AppsHeader(
                count = source.size,
                filter = filter,
                hidden = current.content.hidden.size,
                order = order,
                query = query,
                onOpen = { open = it },
            )
        },
        showLabels = current.appearance.showLabels || filter == AppFilter.Hidden,
        hidden = filter == AppFilter.Hidden,
        size = current.appearance.tileSize,
        banner = { current.appearance.customBanners[it.id.flatten()] },
        onOpen = viewModel::open,
        onMenu = {
            viewModel.onMenuOpened()
            menu.open(HomeDialog.AppActions(it, Place.All))
        },
        anchor = menu.anchor,
        modifier = modifier,
    )
    open?.let { shown ->
        AppsDialogs(
            dialog = shown,
            query = query,
            hidden = current.content.hidden.size,
            onQuery = { query = it },
            onOrder = viewModel::setAppsOrder,
            onFilter = { chosen = it },
            onDismiss = { open = null },
        )
    }
    menu.dialog?.let { shown ->
        HomeDialogs(
            dialog = shown,
            state = current,
            commands = viewModel,
            show = { menu.dialog = it },
            startMoving = {},
            anchor = menu.at,
        )
    }
}

/** [source] filtered and sorted by name; sorting by use puts the most opened in [OrderedApps.top]. */
private fun orderedApps(
    source: List<App>,
    filter: AppFilter,
    words: String,
    order: AppsOrder,
    usage: Map<String, Long>,
): OrderedApps {
    val shown = source.filter { app ->
        val kind = when (filter) {
            AppFilter.Tv -> app.isTvApp
            AppFilter.Phone -> !app.isTvApp
            AppFilter.All, AppFilter.Hidden -> true
        }
        kind && (words.isEmpty() || app.label.contains(words, ignoreCase = true))
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    val byUse = order == AppsOrder.ByUse && filter != AppFilter.Hidden && words.isEmpty()
    val top = if (byUse) mostUsed(shown, usage, TOP_APPS) else emptyList()
    return OrderedApps(top, shown - top.toSet())
}

@Composable
private fun AppsDialogs(
    dialog: AppsDialog,
    query: String,
    hidden: Int,
    onQuery: (String) -> Unit,
    onOrder: (AppsOrder) -> Unit,
    onFilter: (AppFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        AppsDialog.Find -> NameDialog(
            title = stringResource(R.string.apps_find_title),
            initial = query,
            saveLabel = stringResource(R.string.apps_find),
            cancelLabel = stringResource(R.string.name_cancel),
            onSave = {
                onQuery(it)
                onDismiss()
            },
            onDismiss = onDismiss,
        )

        AppsDialog.Order -> ChoiceDialog(
            title = stringResource(R.string.apps_order),
            choices = AppsOrder.entries.map { entry -> Choice(stringResource(entry.title)) { onOrder(entry) } },
            onDismiss = onDismiss,
        )

        AppsDialog.Show -> ChoiceDialog(
            title = stringResource(R.string.apps_show),
            choices = AppFilter.entries
                .filter { it != AppFilter.Hidden || hidden > 0 }
                .map { entry -> Choice(filterTitle(entry, hidden)) { onFilter(entry) } },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun AppsGrid(
    apps: OrderedApps,
    header: @Composable () -> Unit,
    showLabels: Boolean,
    hidden: Boolean,
    size: TileSize,
    banner: (App) -> String?,
    onOpen: (App) -> Unit,
    onMenu: (App) -> Unit,
    anchor: TileAnchor,
    modifier: Modifier = Modifier,
) {
    val topTitle = stringResource(R.string.apps_most_used)
    val restTitle = stringResource(R.string.apps_the_rest)
    // The screen opens on its first app; a frame later, once the grid has laid its tiles out.
    val firstTile = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { firstTile.requestFocus() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(size.columns),
        contentPadding = GRID_PADDING,
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        modifier = modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { header() }
        // Down from the last row goes nowhere, explicitly: otherwise focus search lays out the
        // hint below the grid on every repeat of a held key.
        val tile: @Composable (App, Boolean, Boolean) -> Unit = { app, first, lastRow ->
            AppCard(
                app = app,
                onClick = { onOpen(app) },
                onLongClick = { onMenu(app) },
                showLabel = showLabels,
                banner = banner(app),
                modifier = Modifier
                    .then(if (first) Modifier.focusRequester(firstTile) else Modifier)
                    .anchorWhenFocused(anchor)
                    .focusProperties { if (lastRow) down = FocusRequester.Cancel },
            )
        }
        val columns = size.columns
        if (apps.top.isNotEmpty()) {
            label(topTitle)
            itemsIndexed(apps.top, key = { _, app -> app.id.flatten() }) { index, app ->
                tile(app, index == 0, apps.rest.isEmpty() && index >= lastRowStart(apps.top.size, columns))
            }
            if (apps.rest.isNotEmpty()) label(restTitle)
        }
        itemsIndexed(apps.rest, key = { _, app -> app.id.flatten() }) { index, app ->
            tile(app, index == 0 && apps.top.isEmpty(), index >= lastRowStart(apps.rest.size, columns))
        }
        if (apps.top.isEmpty() && apps.rest.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.apps_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(if (hidden) R.string.apps_hint_hidden else R.string.home_hold_ok_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

/** Index of the first item in the last row of [count] items laid out [columns] to a row. */
private fun lastRowStart(count: Int, columns: Int): Int = count - ((count - 1) % columns + 1)

private fun LazyGridScope.label(text: String) = item(key = "label:$text", span = { GridItemSpan(maxLineSpan) }) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Three buttons, the most used first: find, the order, what to show (the rarer filters live in there). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppsHeader(
    count: Int,
    filter: AppFilter,
    hidden: Int,
    order: AppsOrder,
    query: String,
    onOpen: (AppsDialog) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(bottom = 16.dp),
    ) {
        Text(stringResource(R.string.home_all_apps), style = MaterialTheme.typography.headlineMedium)
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        FilterChip(selected = query.isNotBlank(), onClick = { onOpen(AppsDialog.Find) }) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(query.ifBlank { stringResource(R.string.apps_find) }, maxLines = 1)
        }
        FilterChip(selected = false, onClick = { onOpen(AppsDialog.Order) }) {
            Text(
                stringResource(
                    if (order == AppsOrder.ByUse) R.string.apps_order_chip_by_use else R.string.apps_order_chip_by_name,
                ),
                maxLines = 1,
            )
        }
        FilterChip(selected = filter != AppFilter.All, onClick = { onOpen(AppsDialog.Show) }) {
            Text(stringResource(R.string.apps_show_chip, filterTitle(filter, hidden)), maxLines = 1)
        }
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

/** How many of the most used apps come first when sorting by use: one row of a medium grid. */
private const val TOP_APPS = 5
