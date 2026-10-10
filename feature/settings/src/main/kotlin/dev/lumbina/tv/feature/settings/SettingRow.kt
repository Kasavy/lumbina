package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import dev.lumbina.tv.core.ui.LAYERS_ARE_CHEAP

/**
 * A row of the settings: a headline with what is before, under and after it. OK runs [onClick],
 * holding it [onLongClick]; [outline] frames the row while it is focused (a row being moved),
 * [dimmed] fades it (the rows around the one being moved).
 *
 * Where layers are cheap ([LAYERS_ARE_CHEAP]) it is tv-material's `ListItem`, which grows a little
 * on focus and so takes a layer per row. Elsewhere it is [LightRow], which has none and shows
 * focus by its background alone: a page of rows is built noticeably faster on a Mi Box 4.
 */
@Suppress("LongParameterList") // one slot per part of the row, as ListItem has
@Composable
internal fun SettingRow(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    outline: Color? = null,
    dimmed: Boolean = false,
) {
    if (LAYERS_ARE_CHEAP) {
        ListItem(
            selected = selected,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            leadingContent = leadingContent?.let { content -> { content() } },
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            trailingContent = trailingContent,
            border = if (outline != null) {
                ListItemDefaults.border(focusedBorder = Border(BorderStroke(OUTLINE_WIDTH, outline)))
            } else {
                ListItemDefaults.border()
            },
            modifier = if (dimmed) modifier.alpha(DIMMED_ALPHA) else modifier,
        )
    } else {
        LightRow(
            onClick = onClick,
            headlineContent = headlineContent,
            modifier = modifier,
            selected = selected,
            enabled = enabled,
            onLongClick = onLongClick,
            leadingContent = leadingContent,
            supportingContent = supportingContent,
            trailingContent = trailingContent,
            outline = outline,
            dimmed = dimmed,
        )
    }
}

private val OUTLINE_WIDTH = 3.dp

/**
 * The row without a layer of its own. Focus shows as the light background, [selected] as a tint,
 * [dimmed] in the color of what it holds (fading the row itself would take a layer). OK clicks on
 * release; a pointer and an accessibility service can click it too.
 */
@Suppress("LongParameterList") // the same slots as SettingRow
@Composable
private fun LightRow(
    onClick: () -> Unit,
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    outline: Color? = null,
    dimmed: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val container = when {
        focused -> colors.inverseSurface
        selected -> colors.secondaryContainer.copy(alpha = SELECTED_ALPHA)
        else -> Color.Transparent
    }
    val content = when {
        focused -> colors.inverseOnSurface
        selected -> colors.onSecondaryContainer
        else -> colors.onSurface
    }.let { if (enabled && !dimmed) it else it.copy(alpha = DIMMED_ALPHA) }
    val ok = remember { OkPress() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // OK is taken here, before the click below sees it: that one clicks on the key going
            // down and knows no long press; it is there for a pointer and for accessibility.
            .onPreviewKeyEvent { ok.handle(it, onClick.takeIf { enabled }, onLongClick.takeIf { enabled }) }
            .focusable(interactionSource = interactions)
            .combinedClickable(
                interactionSource = interactions,
                indication = null,
                enabled = enabled,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .background(container, SHAPE)
            .then(if (focused && outline != null) Modifier.border(OUTLINE_WIDTH, outline, SHAPE) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides content) {
            leadingContent?.invoke()
            Column(Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium, headlineContent)
                if (supportingContent != null) ProvideTextStyle(MaterialTheme.typography.bodySmall, supportingContent)
            }
            if (trailingContent != null) ProvideTextStyle(MaterialTheme.typography.labelLarge, trailingContent)
        }
    }
}

/** One press of OK on a row: a click when released, unless it was held long enough to be a long click. */
private class OkPress {
    private var down = false
    private var held = false

    fun handle(event: KeyEvent, onClick: (() -> Unit)?, onLongClick: (() -> Unit)?): Boolean {
        if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) return false
        when (event.type) {
            KeyEventType.KeyDown -> {
                val first = event.nativeKeyEvent.repeatCount == 0
                val long = down && !held && event.nativeKeyEvent.isLongPress
                if (first) {
                    down = true
                    held = false
                } else if (long && onLongClick != null) {
                    held = true
                    onLongClick()
                }
            }

            // Only a press that began on this row: OK held while the focus arrived isn't a click here.
            KeyEventType.KeyUp -> {
                if (down && !held) onClick?.invoke()
                down = false
            }
        }
        return true
    }
}

private val SHAPE = RoundedCornerShape(8.dp)
private const val SELECTED_ALPHA = 0.4f
private const val DIMMED_ALPHA = 0.4f

/**
 * The color for a row's text that names a problem: the error color, except while the row is
 * [focused], when its light background makes that color pale; then the row's own.
 */
@Composable
internal fun problemColor(focused: Boolean): Color = if (focused) Color.Unspecified else MaterialTheme.colorScheme.error
