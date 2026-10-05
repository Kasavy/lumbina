package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.backdrop.WALLPAPER_CHANGE_MINUTES
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ConfirmDialog

/** Where the home wallpaper comes from, how often it changes, and dimming at night. */
@Composable
fun WallpaperPanel(state: WallpaperPanelState?, commands: WallpaperCommands, modifier: Modifier = Modifier) {
    if (state == null) return
    var deleting by remember { mutableStateOf(false) }
    SettingsList(modifier) {
        builtInSources(state, commands)
        items(state.plugins, key = { it.id }) { plugin -> PluginItem(plugin, state.settings.source, commands) }
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
        if (state.myMedia > 0) {
            item {
                SettingRow(
                    onClick = { deleting = true },
                    headlineContent = { Text(stringResource(R.string.wallpaper_delete_media)) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.wallpaper_files, state.myMedia, state.myMedia))
                    },
                )
            }
        }
    }
    if (deleting) DeleteMediaDialog(state.myMedia, commands::deleteMyMedia, onDismiss = { deleting = false })
}

/** Deleting takes the files off the device, and the banners made from them: one OK is too easy on a remote. */
@Composable
private fun DeleteMediaDialog(files: Int, onDelete: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    ConfirmDialog(
        title = stringResource(R.string.wallpaper_delete_media_title),
        text = stringResource(
            R.string.wallpaper_delete_media_text,
            pluralStringResource(R.plurals.wallpaper_files, files, files),
        ),
        confirmLabel = stringResource(R.string.wallpaper_delete_media_confirm),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = {
            onDelete()
            onDismiss()
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

private fun LazyListScope.builtInSources(state: WallpaperPanelState, commands: WallpaperCommands) {
    val source = state.settings.source
    item {
        SourceItem(
            title = stringResource(R.string.wallpaper_none),
            hint = stringResource(R.string.wallpaper_none_hint, stringResource(state.background.title)),
            selected = source == WallpaperSource.Gradient,
            onClick = { commands.choose(WallpaperSource.Gradient) },
        )
    }
    item {
        SourceItem(
            title = stringResource(R.string.wallpaper_my_media),
            hint = if (state.myMedia == 0) {
                stringResource(R.string.wallpaper_my_media_empty)
            } else {
                pluralStringResource(R.plurals.wallpaper_files, state.myMedia, state.myMedia)
            },
            selected = source == WallpaperSource.MyMedia,
            onClick = { commands.choose(WallpaperSource.MyMedia) },
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

@Composable
private fun SourceItem(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    SettingRow(
        onClick = onClick,
        onLongClick = onSettings,
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        trailingContent = {
            TrailingWithHold(focused, R.drawable.ic_plugin_settings.takeIf { onSettings != null }) {
                RowRadio(selected = selected)
            }
        },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
    )
}
