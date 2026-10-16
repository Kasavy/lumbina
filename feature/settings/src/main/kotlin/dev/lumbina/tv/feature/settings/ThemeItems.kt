package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.designsystem.color
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.domain.theme.Themes
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.PillShape
import dev.lumbina.tv.core.ui.TICK
import dev.lumbina.tv.core.ui.brush

/** The "View and language" page: the theme and its accent, text size, the language, then the background. */
@Composable
fun LookPanel(
    state: HomeSettingsState?,
    commands: HomeSettingsCommands,
    language: String?,
    languageSupported: Boolean,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    SettingsList(modifier) {
        themeItems(state.appearance, commands, language, languageSupported)
        backgroundItems(state.appearance, commands)
    }
}

/** The theme, the accent color, text size and the language. */
internal fun LazyListScope.themeItems(
    appearance: HomeAppearance,
    commands: HomeSettingsCommands,
    language: String?,
    languageSupported: Boolean,
) {
    item {
        ChoiceRow(
            title = stringResource(R.string.theme_theme),
            options = THEMES.map { (id, name) ->
                Choice(stringResource(name), chosen = id == appearance.theme) { commands.setTheme(id) }
            },
        )
    }
    item { Header(stringResource(R.string.theme_header)) }
    item {
        val own = appearance.accent.takeIf { appearance.accentChosen }
        // ▼ from the row above lands on the color in use, not on whichever circle is nearest.
        val chosen = remember { FocusRequester() }
        val mark = { selected: Boolean -> if (selected) Modifier.focusRequester(chosen) else Modifier }
        // A plain row: every color is there for the focus to go to, the chosen one too when it is out of sight.
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .focusRestorer(chosen)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            // First the theme's own highlight, which is what a new setup has: by its name, since
            // its color is one of those that follow.
            val theme = LocalTheme.current.colorOf(ThemeRole.Accent)
            ThemeAccent(theme, own == null, { commands.setAccent(null) }, mark(own == null))
            Accent.entries.forEach { accent ->
                Swatch(accent.color, accent == own, { commands.setAccent(accent) }, mark(accent == own))
            }
        }
    }
    item {
        SwitchRow(
            title = stringResource(R.string.theme_large_text),
            hint = stringResource(R.string.theme_large_text_hint),
            checked = appearance.textScale == TextScale.Large,
            onClick = commands::toggleTextScale,
        )
    }
    if (languageSupported) {
        item { Header(stringResource(R.string.theme_language)) }
        items(AppLanguage.CHOICES) { tag ->
            SettingRow(
                onClick = { commands.setLanguage(tag) },
                headlineContent = {
                    Text(tag?.let(::nativeName) ?: stringResource(R.string.theme_language_system))
                },
                trailingContent = { RowRadio(selected = tag == language) },
            )
        }
    }
}

/** A highlight color to choose. */
@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, shape = CardDefaults.shape(CircleShape), modifier = modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(color)) {
            // Dark or light, whichever reads on this color.
            val mark = if (color.luminance() > MARK_FLIP) Color.Black else Color.White
            Text(if (selected) TICK else "", style = MaterialTheme.typography.titleLarge, color = mark)
        }
    }
}

/** "As in the theme": the highlight the theme itself has, named, with a dot of its [color]. */
@Composable
private fun ThemeAccent(color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val pill = CardDefaults.shape(PillShape)
    Card(onClick = onClick, shape = pill, modifier = modifier.height(64.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
        ) {
            Box(Modifier.size(20.dp).background(color, CircleShape))
            Text(stringResource(R.string.theme_accent_theme), style = MaterialTheme.typography.titleSmall, maxLines = 1)
            if (selected) Text(TICK, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The themes to choose from, as their ids and names. */
private val THEMES = listOf(
    Themes.Dark.id to R.string.theme_dark,
    Themes.Light.id to R.string.theme_light,
    Themes.Black.id to R.string.theme_black,
    HomeAppearance.THEME_SYSTEM to R.string.theme_system,
)

private const val MARK_FLIP = 0.5f

/** The built-in gradients behind the home screen (shown under Look). */
internal fun LazyListScope.backgroundItems(appearance: HomeAppearance, commands: HomeSettingsCommands) {
    item { Header(stringResource(R.string.home_background)) }
    item {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            items(HomeBackground.entries) { background ->
                BackgroundSwatch(
                    background = background,
                    selected = background == appearance.background,
                    onClick = { commands.setBackground(background) },
                )
            }
        }
    }
}

@Composable
private fun BackgroundSwatch(
    background: HomeBackground,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.size(width = 192.dp, height = 108.dp)) {
        Box(
            contentAlignment = Alignment.BottomStart,
            modifier = Modifier.fillMaxSize().background(background.brush(LocalTheme.current)).padding(12.dp),
        ) {
            Text(
                text = stringResource(background.title) + if (selected) " $TICK" else "",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

internal val HomeBackground.title: Int
    get() = when (this) {
        HomeBackground.Night -> R.string.background_night
        HomeBackground.Dusk -> R.string.background_dusk
        HomeBackground.Ocean -> R.string.background_ocean
        HomeBackground.Forest -> R.string.background_forest
        HomeBackground.Plum -> R.string.background_plum
    }
