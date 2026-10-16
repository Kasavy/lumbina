package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeSection
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.ui.FreshPressGuard
import dev.lumbina.tv.core.ui.PillShape
import kotlin.math.abs

/** Opens the size slider on the row with this key: the panel steps aside for it. */
@Suppress("CompositionLocalAllowlist")
internal val LocalAdjustSize = staticCompositionLocalOf<(String) -> Unit> { {} }

/** The sizes the row can have, the smallest tiles first: every size for app tiles, three for cards of programs. */
internal fun RowPageOf.sizes(): List<TileSize> =
    TileSize.entries.sortedByDescending { it.perRow }.filter { ofApps || it.perRow in CARD_SIZES }

/** A size as the row's settings call it: "6 per row" for tiles, a name for cards. */
@Composable
internal fun TileSize.nameFor(page: RowPageOf): String = if (page.ofApps) {
    stringResource(R.string.tile_per_row, perRow)
} else {
    stringResource(
        when (this) {
            TileSize.Large -> R.string.card_large
            TileSize.Medium -> R.string.card_medium
            else -> R.string.card_small
        },
    )
}

/**
 * The size of a row's tiles, set on the home screen itself: the settings' panel is out of the way,
 * home is brought to the row, and a card at the bottom says which row and what size. ◀ ▶ change
 * the size at once, ▲ ▼ go to the row above or below, OK keeps what was set, Back puts every row
 * back as it was when the slider opened.
 */
@Composable
internal fun SizeSlider(start: String, onDone: () -> Unit, viewModel: HomeSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    var key by rememberSaveable { mutableStateOf(start) }
    // The rows' own sizes as they were: what Back goes back to.
    val before = remember { current.appearance.rowLooks.mapValues { it.value.tileSize } }
    val rows = current.rows.filter { it.visible }.map { it.row.key }
    val page = current.rowPage(key) ?: current.rowPage(HomeSection.FAVORITES) ?: return
    // A row switched off has nothing on home to show the size on.
    val note = stringResource(R.string.slider_row_hidden).takeIf { page.key !in rows }
    // The row lit before (the page the slider was opened from) is lit again after.
    val litBefore = remember { viewModel.spotlit }
    LaunchedEffect(page.key) { viewModel.spotlight(page.key.takeIf { note == null }) }
    DisposableEffect(Unit) { onDispose { viewModel.spotlight(litBefore) } }
    val sizes = page.sizes()
    val now = current.appearance.tileSizeOf(page.key)
    val step = { by: Int -> viewModel.stepRowTileSize(page.key, by, sizes) }
    // ▲▼ go over the rows shown; from one switched off, to the shown ones around its place.
    val all = current.rows.map { it.row.key }
    val around = if (page.key in rows) rows else (rows + page.key).sortedBy { all.indexOf(it) }
    val go = { by: Int -> around.getOrNull(around.indexOf(page.key) + by)?.let { key = it } }
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = {
            viewModel.setRowTileSizes(before)
            onDone()
        },
        properties = PopupProperties(focusable = true),
    ) {
        SliderCard(
            title = rowTitle(page),
            note = note,
            value = now.nameFor(page),
            at = sizes.nearest(now),
            usual = sizes.nearest(HomeAppearance().defaultTileSizeOf(page.key)),
            count = sizes.size,
            onKey = { pressed ->
                when (pressed) {
                    Key.DirectionLeft -> step(-1)
                    Key.DirectionRight -> step(1)
                    Key.DirectionUp -> go(-1)
                    Key.DirectionDown -> go(1)
                    Key.DirectionCenter, Key.Enter -> onDone()
                    else -> Unit
                }
            },
        )
    }
}

/** Where [size] is on a scale of these: a card row set to a size only tiles have counts as the nearest it can show. */
internal fun List<TileSize>.nearest(size: TileSize): Int = indices.minBy { abs(this[it].perRow - size.perRow) }

/**
 * The card: the row's name, the size in words, the scale with the current size marked and a dot
 * under the [usual] one (what a row has until it is set), and the keys.
 */
@Composable
private fun SliderCard(
    title: String,
    note: String?,
    value: String,
    at: Int,
    usual: Int,
    count: Int,
    onKey: (Key) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    // Opened by OK: the press still down is not the one that closes the slider.
    val guard = remember { FreshPressGuard() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(bottom = 28.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .padding(horizontal = 32.dp, vertical = 18.dp)
            .focusRequester(focus)
            .onPreviewKeyEvent(guard::swallow)
            .onKeyEvent { event ->
                // OK acts when let go: its release would otherwise press the row the slider was opened from.
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (event.type == (if (ok) KeyEventType.KeyUp else KeyEventType.KeyDown)) onKey(event.key)
                event.key != Key.Back && event.key != Key.Escape
            }
            .focusable(),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        note?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("◀", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) { index ->
                    val color = MaterialTheme.colorScheme.run { if (index == at) primary else surfaceVariant }
                    val dot = if (index == usual) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent
                    // The dot's room is over the bar too, so the bars stay level with the arrows.
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Box(Modifier.width(44.dp).height(8.dp).background(color, PillShape))
                        Box(Modifier.size(4.dp).background(dot, CircleShape))
                    }
                }
            }
            Text("▶", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(R.string.slider_keys),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Per-row counts that cards of programs come in: small, medium, large. */
private val CARD_SIZES = setOf(TileSize.Small.perRow, TileSize.Medium.perRow, TileSize.Large.perRow)
