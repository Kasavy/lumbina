package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.ui.ConfirmDialog
import dev.lumbina.tv.core.ui.SidePanel
import java.text.DateFormat
import java.util.Date

/**
 * A file's menu: its marks, showing it now, making a photo a banner, deleting. In the pick mode
 * the mark being picked is left out: it is the tick. A banner's menu says where it stands and deletes it.
 */
@Composable
internal fun MediaMenu(entry: MediaEntry, pick: MediaMark?, commands: MediaCommands, onDismiss: () -> Unit) {
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
                val tiles = entry.shownOn.joinToString()
                if (tiles.isNotEmpty()) Note(stringResource(R.string.media_banner_on, tiles))
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
