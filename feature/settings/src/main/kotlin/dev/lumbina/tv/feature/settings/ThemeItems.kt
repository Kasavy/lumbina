package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.designsystem.color
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeBackground
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.ui.brush

/** Settings → Look: the gradient background, the accent color, text size and the language. */
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

/** Accent color, text size and the language. */
internal fun LazyListScope.themeItems(
    appearance: HomeAppearance,
    commands: HomeSettingsCommands,
    language: String?,
    languageSupported: Boolean,
) {
    item { Header(stringResource(R.string.theme_header)) }
    item {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        ) {
            items(Accent.entries) { accent ->
                AccentSwatch(accent, selected = accent == appearance.accent, onClick = { commands.setAccent(accent) })
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

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(CircleShape),
        modifier = modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(accent.color)) {
            if (selected) Text("✓", style = MaterialTheme.typography.titleLarge, color = Color.Black)
        }
    }
}

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
            modifier = Modifier.fillMaxSize().background(background.brush()).padding(12.dp),
        ) {
            Text(
                text = stringResource(background.title) + if (selected) " ✓" else "",
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
