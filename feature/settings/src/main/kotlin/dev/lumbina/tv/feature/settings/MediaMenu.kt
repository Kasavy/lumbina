package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.domain.home.isInputKey
import dev.lumbina.tv.core.ui.ConfirmDialog
import dev.lumbina.tv.core.ui.SidePanel
import java.text.DateFormat
import java.util.Date

/**
 * A file's menu: looking at it over the whole screen ([onView]; not a video that won't play), its
 * marks, showing it now, making a photo a banner, deleting. In the pick mode the mark being picked
 * is left out: it is the tick.
 * A banner's menu says where it stands, puts it back to the wallpaper and deletes it.
 */
@Composable
internal fun MediaMenu(
    entry: MediaEntry,
    pick: MediaMark?,
    commands: MediaCommands,
    onView: () -> Unit,
    onDismiss: () -> Unit,
) {
    var deleting by remember { mutableStateOf(false) }
    val kind = when {
        entry.banner -> R.string.media_kind_banner
        entry.file.kind == MediaKind.Video -> R.string.media_kind_video
        else -> R.string.media_kind_photo
    }
    SidePanel(
        title = stringResource(kind),
        subtitle = stringResource(R.string.media_sent, DateFormat.getDateInstance(DateFormat.SHORT).format(entry.date)),
        path = listOf(stringResource(R.string.media_title)),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (entry.banner) {
                val tiles = entry.tileNames()
                if (tiles.isNotEmpty()) Note(stringResource(R.string.media_banner_on, tiles))
            }
            // A video that won't play here would show only its frame.
            if (!entry.unplayable) {
                SettingRow(onClick = onView, headlineContent = { Text(stringResource(R.string.media_view)) })
            }
            if (entry.banner) {
                BannerEntries(entry, commands, onDismiss)
            } else {
                FileEntries(entry, pick, commands, onDismiss)
            }
            SettingRow(
                onClick = { deleting = true },
                headlineContent = { Text(stringResource(R.string.media_delete)) },
                supportingContent = {
                    val shown = entry.shownOn.isNotEmpty()
                    val hint = if (shown) R.string.media_delete_banner_hint else R.string.media_delete_hint
                    Text(stringResource(hint))
                },
            )
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.media_delete_title),
            text = entry.file.title,
            confirmLabel = stringResource(R.string.wallpaper_delete_media_confirm),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                commands.delete(entry)
                deleting = false
                onDismiss()
            },
            onDismiss = { deleting = false },
        )
    }
}

private val MediaEntry.date: Date get() = Date(file.addedAt)

/** A banner back to the wallpaper: a copy of it, when tiles show it. */
@Composable
private fun BannerEntries(entry: MediaEntry, commands: MediaCommands, onDone: () -> Unit) {
    val shown = entry.shownOn.isNotEmpty()
    SettingRow(
        onClick = {
            commands.toWallpaper(entry)
            onDone()
        },
        headlineContent = { Text(stringResource(R.string.media_to_wallpaper)) },
        supportingContent = {
            Text(stringResource(if (shown) R.string.media_to_wallpaper_copy_hint else R.string.media_to_wallpaper_hint))
        },
    )
}

/** What a photo or video of the wallpaper and screensaver can be made to do. */
@Composable
private fun FileEntries(entry: MediaEntry, pick: MediaMark?, commands: MediaCommands, onDone: () -> Unit) {
    if (pick != MediaMark.Wallpaper) {
        SwitchRow(
            title = stringResource(R.string.media_mark_wallpaper),
            hint = stringResource(R.string.media_mark_wallpaper_hint),
            checked = entry.onWallpaper,
            onClick = { commands.mark(entry, MediaMark.Wallpaper, !entry.onWallpaper) },
        )
    }
    if (pick != MediaMark.Screensaver) {
        SwitchRow(
            title = stringResource(R.string.media_mark_screensaver),
            hint = stringResource(R.string.media_mark_screensaver_hint),
            checked = entry.onScreensaver,
            onClick = { commands.mark(entry, MediaMark.Screensaver, !entry.onScreensaver) },
        )
    }
    SettingRow(
        onClick = {
            commands.showNow(entry)
            onDone()
        },
        headlineContent = { Text(stringResource(R.string.media_show_now)) },
    )
    // A banner is a still picture: a video can't be one.
    if (entry.file.kind == MediaKind.Photo) {
        SettingRow(
            onClick = {
                commands.toBanner(entry)
                onDone()
            },
            headlineContent = { Text(stringResource(R.string.media_to_banner)) },
            supportingContent = { Text(stringResource(R.string.media_to_banner_hint)) },
        )
    }
}

/** The tiles that show this banner: the apps' by name, then those of Lumbina's own. */
@Composable
@ReadOnlyComposable
internal fun MediaEntry.tileNames(): String {
    val own = onOwnTiles.map { key ->
        stringResource(if (isInputKey(key)) R.string.media_on_input else R.string.media_on_all_apps)
    }
    return (shownOn + own.distinct()).joinToString()
}
