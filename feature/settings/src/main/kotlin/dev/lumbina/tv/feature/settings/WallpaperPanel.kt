package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.backdrop.WALLPAPER_CHANGE_MINUTES
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.ui.Choice

/** Where the home wallpaper comes from, how often it changes, and dimming at night. */
@Composable
fun WallpaperPanel(state: WallpaperPanelState?, commands: WallpaperCommands, modifier: Modifier = Modifier) {
    if (state == null) return
    SettingsList(modifier) {
        builtInSources(state, commands)
        items(state.plugins, key = { it.id }) { plugin -> PluginItem(plugin, state.shownSource, commands) }
        item { Note(stringResource(R.string.wallpaper_plugins_note)) }
        item { Header(stringResource(R.string.wallpaper_options)) }
        item {
            ChoiceRow(
                title = stringResource(R.string.wallpaper_change_every),
                options = WALLPAPER_CHANGE_MINUTES.map { minutes ->
                    Choice(
                        stringResource(R.string.wallpaper_minutes, minutes),
                        chosen = minutes == state.settings.changeMinutes,
                    ) {
                        commands.setChangeInterval(minutes)
                    }
                },
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.wallpaper_dim_at_night),
                hint = stringResource(R.string.wallpaper_dim_at_night_hint),
                checked = state.settings.dimAtNight,
                onClick = commands::toggleDimAtNight,
            )
        }
    }
}

/** The source as it shows: a plugin removed since it was chosen gives nothing, as the gradient. */
private val WallpaperPanelState.shownSource: WallpaperSource
    get() = settings.source.let { source ->
        val gone = source is WallpaperSource.Plugin && plugins.none { it.id == source.id }
        if (gone) WallpaperSource.Gradient else source
    }

private fun LazyListScope.builtInSources(state: WallpaperPanelState, commands: WallpaperCommands) {
    val source = state.shownSource
    item {
        SourceItem(
            title = stringResource(R.string.wallpaper_none),
            hint = stringResource(R.string.wallpaper_none_hint, stringResource(state.background.title)),
            selected = source == WallpaperSource.Gradient,
            onClick = { commands.choose(WallpaperSource.Gradient) },
        )
    }
    item {
        MyMediaSource(
            pick = MediaMark.Wallpaper,
            hint = if (state.myMedia == 0) {
                stringResource(R.string.wallpaper_my_media_empty)
            } else {
                stringResource(R.string.wallpaper_my_media_hint, state.onWallpaper, state.myMedia)
            },
            selected = source == WallpaperSource.MyMedia,
            onChoose = { commands.choose(WallpaperSource.MyMedia) },
        )
    }
    item {
        SourceItem(
            title = stringResource(R.string.wallpaper_photo_of_the_day),
            hint = stringResource(R.string.wallpaper_photo_of_the_day_hint),
            selected = source == WallpaperSource.PhotoOfTheDay,
            onClick = { commands.choose(WallpaperSource.PhotoOfTheDay) },
        )
    }
}

/**
 * The user's own photos and videos as the wallpaper's or screensaver's source: OK chooses them;
 * holding OK chooses them too and opens the library to [pick] which, so what is picked there shows.
 */
@Composable
internal fun MyMediaSource(pick: MediaMark, hint: String, selected: Boolean, onChoose: () -> Unit) {
    val links = LocalSettingsLinks.current
    SourceItem(
        title = stringResource(R.string.wallpaper_my_media),
        hint = hint,
        selected = selected,
        onClick = onChoose,
        onSettings = {
            onChoose()
            links.media(MediaView(pick = pick))
        },
        holdIcon = R.drawable.ic_page_media,
    )
}

/** A plugin: OK chooses it, and holding OK opens its own settings, if it has any. */
@Composable
private fun PluginItem(plugin: WallpaperPluginInfo, source: WallpaperSource, commands: WallpaperCommands) {
    SourceItem(
        title = plugin.name,
        hint = stringResource(R.string.wallpaper_plugin),
        selected = source == WallpaperSource.Plugin(plugin.id),
        onClick = { commands.choose(WallpaperSource.Plugin(plugin.id)) },
        onSettings = if (plugin.hasSettings) ({ commands.openPluginSettings(plugin.id) }) else null,
    )
}

/** One place pictures come from: OK chooses it; with [onSettings], holding OK opens what it has to pick. */
@Composable
internal fun SourceItem(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    holdIcon: Int = R.drawable.ic_plugin_settings,
) {
    var focused by remember { mutableStateOf(false) }
    SettingRow(
        onClick = onClick,
        onLongClick = onSettings,
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        trailingContent = {
            TrailingWithHold(focused, holdIcon.takeIf { onSettings != null }) {
                RowRadio(selected = selected)
            }
        },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    )
}
