package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.sleep.AUTO_SLEEP_MINUTES
import dev.lumbina.tv.core.domain.sleep.SCREENSAVER_CHANGE_MINUTES
import dev.lumbina.tv.core.domain.sleep.SCREENSAVER_MINUTES
import dev.lumbina.tv.core.domain.sleep.ScreensaverPhotos
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.CommandsDialog

/**
 * The screensaver, its photos chosen from the same list as the wallpaper's, then auto-sleep. When
 * Lumbina may not set the system screensaver, [openAccess] (null where this device has no Access
 * page) leads to the access that lets it.
 */
@Composable
fun SleepPanel(
    state: SleepPanelState?,
    commands: SleepSettingsCommands,
    onPreviewScreensaver: () -> Unit,
    modifier: Modifier = Modifier,
    openAccess: (() -> Unit)? = null,
    computerCommands: List<String> = emptyList(),
) {
    if (state == null) return
    SettingsList(modifier) {
        photoSources(state, commands)
        item { Header(stringResource(R.string.wallpaper_options)) }
        item { DelayRow(state, commands) }
        item {
            ChoiceRow(
                title = stringResource(R.string.wallpaper_change_every),
                hint = stringResource(R.string.screensaver_change_hint),
                options = SCREENSAVER_CHANGE_MINUTES.map { minutes ->
                    Choice(
                        stringResource(R.string.wallpaper_minutes, minutes),
                        chosen = minutes == state.sleep.screensaverChangeMinutes,
                    ) {
                        commands.setScreensaverChange(minutes)
                    }
                },
            )
        }
        item {
            SettingRow(
                onClick = commands::selectScreensaver,
                headlineContent = { Text(stringResource(R.string.screensaver_use_lumbina)) },
                supportingContent = {
                    val hint = when {
                        state.screensaverIsLumbina -> R.string.screensaver_is_lumbina
                        else -> R.string.screensaver_not_lumbina
                    }
                    Text(stringResource(hint))
                },
            )
        }
        if (state.screensaverDenied) item { ScreensaverDenied(openAccess, computerCommands) }
        item {
            SettingRow(
                onClick = onPreviewScreensaver,
                headlineContent = { Text(stringResource(R.string.screensaver_preview)) },
            )
        }
        item { Header(stringResource(R.string.sleep_header)) }
        item {
            ChoiceRow(
                title = stringResource(R.string.sleep_auto),
                hint = stringResource(R.string.sleep_auto_hint),
                options = AUTO_SLEEP_MINUTES.map { minutes ->
                    Choice(autoSleepLabel(minutes), chosen = minutes == state.sleep.autoSleepMinutes) {
                        commands.setAutoSleep(minutes)
                    }
                },
            )
        }
        item { Note(stringResource(R.string.sleep_timer_note)) }
    }
}

/** What the screensaver shows: the wallpaper's choice, or one of the same sources as the wallpaper's. */
private fun LazyListScope.photoSources(state: SleepPanelState, commands: SleepSettingsCommands) {
    // A plugin removed since it was chosen gives nothing: photos of the day show, as here.
    val chosen = state.sleep.screensaverPhotos.let { photos ->
        val gone = photos is ScreensaverPhotos.Plugin && state.plugins.none { it.id == photos.id }
        if (gone) ScreensaverPhotos.PhotoOfTheDay else photos
    }
    item {
        SourceItem(
            title = stringResource(R.string.screensaver_as_wallpaper),
            hint = asWallpaperHint(state),
            selected = chosen == ScreensaverPhotos.AsWallpaper,
            onClick = { commands.setScreensaverPhotos(ScreensaverPhotos.AsWallpaper) },
        )
    }
    item {
        MyMediaSource(
            pick = MediaMark.Screensaver,
            hint = if (state.files == 0) {
                stringResource(R.string.wallpaper_my_media_empty)
            } else {
                stringResource(R.string.screensaver_my_media_hint, state.marked, state.files)
            },
            selected = chosen == ScreensaverPhotos.MyMedia,
            onChoose = { commands.setScreensaverPhotos(ScreensaverPhotos.MyMedia) },
        )
    }
    item {
        SourceItem(
            title = stringResource(R.string.wallpaper_photo_of_the_day),
            hint = stringResource(R.string.screensaver_photo_of_the_day_hint),
            selected = chosen == ScreensaverPhotos.PhotoOfTheDay,
            onClick = { commands.setScreensaverPhotos(ScreensaverPhotos.PhotoOfTheDay) },
        )
    }
    items(state.plugins, key = { it.id }) { plugin -> PluginItem(plugin, chosen, commands) }
    item { Note(stringResource(R.string.wallpaper_plugins_note)) }
}

/** What the wallpaper shows now, which this choice follows. */
@Composable
private fun asWallpaperHint(state: SleepPanelState): String {
    val shown = when (val source = state.wallpaper) {
        WallpaperSource.Gradient -> return stringResource(R.string.screensaver_as_no_wallpaper_hint)

        WallpaperSource.MyMedia -> stringResource(R.string.wallpaper_my_media)

        WallpaperSource.PhotoOfTheDay -> stringResource(R.string.wallpaper_photo_of_the_day)

        is WallpaperSource.Plugin -> state.plugins.firstOrNull { it.id == source.id }?.name
            ?: stringResource(R.string.wallpaper_plugin)
    }
    return stringResource(R.string.screensaver_as_wallpaper_hint, shown)
}

/** A plugin: OK chooses it, and holding OK opens its own settings, if it has any. */
@Composable
private fun PluginItem(plugin: WallpaperPluginInfo, chosen: ScreensaverPhotos, commands: SleepSettingsCommands) {
    SourceItem(
        title = plugin.name,
        hint = stringResource(R.string.wallpaper_plugin),
        selected = chosen == ScreensaverPhotos.Plugin(plugin.id),
        onClick = { commands.setScreensaverPhotos(ScreensaverPhotos.Plugin(plugin.id)) },
        onSettings = if (plugin.hasSettings) ({ commands.openPluginSettings(plugin.id) }) else null,
    )
}

@Composable
private fun DelayRow(state: SleepPanelState, commands: SleepSettingsCommands) {
    ChoiceRow(
        title = stringResource(R.string.screensaver_delay),
        hint = stringResource(R.string.screensaver_delay_hint),
        options = SCREENSAVER_MINUTES.map { minutes ->
            val label = minutes?.let { stringResource(R.string.screensaver_after_minutes, it) }
                ?: stringResource(R.string.sleep_auto_off)
            Choice(label, chosen = minutes == state.sleep.screensaverMinutes) {
                commands.setScreensaverDelay(minutes)
            }
        },
    )
}

/** Lumbina may not set the system screensaver: a way to the access that lets it, or the commands for a computer. */
@Composable
private fun ScreensaverDenied(openAccess: (() -> Unit)?, computerCommands: List<String>) {
    if (openAccess != null) {
        SettingRow(
            onClick = openAccess,
            headlineContent = { Text(stringResource(R.string.screensaver_needs_access)) },
        )
        return
    }
    var showing by remember { mutableStateOf(false) }
    SettingRow(
        onClick = { showing = true },
        headlineContent = { Text(stringResource(R.string.screensaver_from_computer)) },
        supportingContent = { Text(stringResource(R.string.screensaver_denied)) },
    )
    if (showing) {
        CommandsDialog(
            title = stringResource(R.string.screensaver_from_computer),
            text = stringResource(R.string.screensaver_computer_text),
            lines = computerCommands,
            onDismiss = { showing = false },
        )
    }
}

@Composable
private fun autoSleepLabel(minutes: Int?): String = when (minutes) {
    null -> stringResource(R.string.sleep_auto_off)
    else -> stringResource(R.string.sleep_auto_hours, minutes / MINUTES_PER_HOUR)
}

private const val MINUTES_PER_HOUR = 60
