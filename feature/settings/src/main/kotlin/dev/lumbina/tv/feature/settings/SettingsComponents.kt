package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.RadioButtonDefaults
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.LAYERS_ARE_CHEAP
import dev.lumbina.tv.core.ui.MenuDialog

/**
 * Fades a page's list out at an edge it scrolls past, so a row cut by the edge doesn't look broken.
 * Only where layers are cheap ([LAYERS_ARE_CHEAP]): the fade draws into a layer of its own while
 * the list can scroll.
 */
internal fun Modifier.fadingEdges(state: ScrollableState): Modifier {
    if (!LAYERS_ARE_CHEAP) return this
    return drawWithCache {
        val edge = FADING_EDGE.toPx()
        // Wider than the list: the focused row grows past its sides, and a layer of the list's
        // own width would cut it there.
        val bleed = FADING_BLEED.toPx()
        val bounds = Rect(-bleed, 0f, size.width + bleed, size.height)
        val strip = Size(bounds.width, edge)
        val bottom = size.height - edge
        val fadeIn = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = edge)
        val fadeOut = Brush.verticalGradient(
            listOf(Color.Black, Color.Transparent),
            startY = bottom,
            endY = size.height,
        )
        val paint = Paint()
        onDrawWithContent {
            val above = state.canScrollBackward
            val below = state.canScrollForward
            if (!above && !below) {
                drawContent()
                return@onDrawWithContent
            }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(bounds, paint)
                drawContent()
                if (above) drawRect(fadeIn, Offset(-bleed, 0f), strip, blendMode = BlendMode.DstIn)
                if (below) drawRect(fadeOut, Offset(-bleed, bottom), strip, blendMode = BlendMode.DstIn)
                canvas.restore()
            }
        }
    }
}

private val CHOSEN_WIDTH = 360.dp
private val FADING_EDGE = 40.dp
private val FADING_BLEED = 48.dp

/** A group title inside a settings page. */
@Composable
internal fun Header(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** A label with its value below; focusable only so the D-pad can scroll the page. */
@Composable
internal fun Fact(label: String, value: String, modifier: Modifier = Modifier, problem: Boolean = false) {
    SettingRow(
        onClick = {},
        headlineContent = { Text(label) },
        // Otherwise the row's own color: a fixed one stays pale on the focused row's light background.
        supportingContent = {
            Text(value, color = if (problem) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        modifier = modifier,
    )
}

/**
 * A setting that is on or off; the whole row toggles it, holding OK runs [onLongClick] if given,
 * and [holdIcon] (what holding does) is shown next to "hold OK" while the row is focused.
 */
@Composable
internal fun SwitchRow(
    title: String,
    hint: String?,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    holdIcon: Int? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val trailing: @Composable () -> Unit = {
        TrailingWithHold(focused, holdIcon) { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    }
    SettingRow(
        enabled = enabled,
        onClick = onClick,
        onLongClick = onLongClick,
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it) } },
        trailingContent = trailing,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    )
}

/**
 * The mark of the chosen one among a list's rows. On the focused row, which is light, it takes
 * the row's content color: the default pale ring and accent dot are lost there.
 */
@Composable
internal fun RowRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val content = LocalContentColor.current
    val onLight = content.luminance() < HALF
    RadioButton(
        selected = selected,
        onClick = null,
        modifier = modifier,
        colors = RadioButtonDefaults.colors(
            selectedColor = if (onLight) content else MaterialTheme.colorScheme.primary,
            unselectedColor = content,
        ),
    )
}

private const val HALF = 0.5f

/**
 * A setting with a handful of values: the row shows the chosen one with an arrow, and OK opens
 * the list of them all ([ChoiceDialog]).
 */
@Composable
internal fun ChoiceRow(title: String, options: List<Choice>, modifier: Modifier = Modifier, hint: String? = null) {
    var choosing by remember { mutableStateOf(false) }
    SettingRow(
        onClick = { choosing = true },
        headlineContent = { Text(title) },
        supportingContent = hint?.let { { Text(it) } },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = options.firstOrNull { it.chosen }?.title.orEmpty(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = CHOSEN_WIDTH),
                )
                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, Modifier.size(20.dp))
            }
        },
        modifier = modifier,
    )
    if (choosing) ChoiceDialog(title = title, choices = options, onDismiss = { choosing = false })
}

/** A row's trailing part: [content], and before it "hold OK" with [holdIcon] while the row is [focused]. */
@Composable
internal fun TrailingWithHold(focused: Boolean, holdIcon: Int?, content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (focused && holdIcon != null) HoldOkHint(holdIcon)
        content()
    }
}

/**
 * A settings page that is a list of rows: it fades at an edge it goes on past and ends with room
 * under its last row.
 */
@Composable
internal fun SettingsList(modifier: Modifier = Modifier, spacing: Dp = 4.dp, content: LazyListScope.() -> Unit) {
    val edges = rememberLazyListState()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing),
        contentPadding = PaddingValues(bottom = PAGE_END),
        state = edges,
        modifier = modifier.fadingEdges(edges),
        content = content,
    )
}

/** As [SettingsList], for a page laid out as a column. */
@Composable
internal fun SettingsColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val edges = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fadingEdges(edges).verticalScroll(edges).padding(bottom = PAGE_END),
        content = content,
    )
}

private val PAGE_END = 48.dp

/** "hold OK" and an [icon] for what that does, shown on the focused row. */
@Composable
internal fun HoldOkHint(icon: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(stringResource(R.string.hold_ok), style = MaterialTheme.typography.bodySmall)
        Text(
            "OK",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .border(BorderStroke(1.5.dp, LocalContentColor.current), CircleShape)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp))
    }
}
