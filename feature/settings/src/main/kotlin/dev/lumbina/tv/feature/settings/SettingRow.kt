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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.ui.DIMMED_ALPHA
import dev.lumbina.tv.core.ui.LAYERS_ARE_CHEAP
import dev.lumbina.tv.core.ui.OkPress
import dev.lumbina.tv.core.ui.okPress

/**
 * A row of the settings: a headline with what is before, under and after it. OK runs [onClick],
 * holding it [onLongClick]; [outline] frames the row while it is focused (a row being moved),
 * [dimmed] fades it (the rows around the one being moved).
 *
 * Where layers are cheap ([LAYERS_ARE_CHEAP]) it is tv-material's `ListItem`, which grows a little
 * on focus and so takes a layer per row. Elsewhere it is [LightRow], which has none and shows
 * focus by its background alone: a layer is a View there, slow to make for every row of a page.
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
        val interactions = remember { MutableInteractionSource() }
        CompositionLocalProvider(LocalRowFocused provides interactions.collectIsFocusedAsState()) {
            ListItem(
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                leadingContent = leadingContent?.let { content -> { content() } },
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                trailingContent = trailingContent,
                interactionSource = interactions,
                border = if (outline != null) {
                    ListItemDefaults.border(focusedBorder = Border(BorderStroke(OUTLINE_WIDTH, outline)))
                } else {
                    ListItemDefaults.border()
                },
                modifier = if (dimmed) modifier.alpha(DIMMED_ALPHA) else modifier,
            )
        }
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
    val focusedState = interactions.collectIsFocusedAsState()
    val focused by focusedState
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
            // OK is taken here, before the click below sees it (see OkPress); that one is there for
            // a pointer and for accessibility. Focusable apart from it: a disabled row is focused too.
            .okPress(ok, onClick.takeIf { enabled }, onLongClick.takeIf { enabled })
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
        CompositionLocalProvider(LocalContentColor provides content, LocalRowFocused provides focusedState) {
            leadingContent?.invoke()
            Column(Modifier.weight(1f)) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium, headlineContent)
                if (supportingContent != null) ProvideTextStyle(MaterialTheme.typography.bodySmall, supportingContent)
            }
            if (trailingContent != null) ProvideTextStyle(MaterialTheme.typography.labelLarge, trailingContent)
        }
    }
}

private val SHAPE = RoundedCornerShape(8.dp)
private const val SELECTED_ALPHA = 0.4f

/**
 * Whether the [SettingRow] around is focused: its text sits on the light background then. A local
 * because the text that needs it is given to the row as a slot and can't be told otherwise. It
 * holds the state itself, which stays the same object: a change of focus recomposes only the
 * text that reads it, not every slot of the row.
 */
@Suppress("CompositionLocalAllowlist")
internal val LocalRowFocused = staticCompositionLocalOf<State<Boolean>> { NotInRow }

private object NotInRow : State<Boolean> {
    override val value = false
}

/**
 * The warning color, for a row's text that names something to fix. On a focused row the color
 * would be pale on the light background; the row's own text color is used there.
 */
@Composable
internal fun rowWarning(): Color = rowColor(LocalTheme.current.colorOf(ThemeRole.Warning))

/** As [rowWarning], in the error color: for a row's text that names a problem, not a step to take. */
@Composable
internal fun rowProblem(): Color = rowColor(MaterialTheme.colorScheme.error)

@Composable
private fun rowColor(color: Color): Color = if (LocalRowFocused.current.value) Color.Unspecified else color
