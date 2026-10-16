package dev.lumbina.tv.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.FreshPressGuard
import dev.lumbina.tv.core.ui.PanelRow
import dev.lumbina.tv.core.ui.SidePanel
import dev.lumbina.tv.core.ui.line

/** What the notifications panel can do; grouped to keep signatures short. */
class StatusActions(
    /** Opens what the notification points to and takes it off the list; false when it did not open. */
    val openNotice: (NoticeUi) -> Boolean,
    val dismissNotice: (NoticeUi) -> Unit,
    /** No more notifications of this notice's app, here in Lumbina. */
    val muteApp: (NoticeUi) -> Unit,
    val clearNotices: () -> Unit,
    /** The last notification went while the panel was open: the panel and the bell are gone with it. */
    val noticesGone: () -> Unit,
)

/**
 * Other apps' notifications, in the side panel. OK opens one and takes it off the list (one with
 * nothing to open is just taken off); holding OK opens two entries under it: take it off, or show
 * nothing of that app any more. "Clear all" comes last, away from where the focus starts: what is
 * cleared can't be brought back. With nothing left the panel closes by itself.
 */
@Composable
internal fun NoticesPanel(notices: List<NoticeUi>, actions: StatusActions, onDismiss: () -> Unit) {
    // The notice whose two entries are open, by key.
    var held by remember { mutableStateOf<String?>(null) }
    // A notice that is gone has nothing open under it: Back then closes the panel at once.
    val expanded = held.takeIf { key -> notices.any { it.key == key } }
    val first = remember { FocusRequester() }
    val close by rememberUpdatedState(onDismiss)
    LaunchedEffect(notices.isEmpty()) { if (notices.isEmpty()) close() }
    // As the panel opens, and when a notification goes with the focus on it.
    var within by remember { mutableStateOf(false) }
    LaunchedEffect(notices.size) {
        withFrameNanos { }
        if (!within) runCatching { first.requestFocus() }
    }
    SidePanel(
        title = stringResource(R.string.status_notifications),
        // Back closes the open entries first, then the panel.
        onDismiss = { if (expanded != null) held = null else onDismiss() },
        subtitle = stringResource(R.string.notices_hint),
        // A few notifications are a few rows, not the screen's height.
        fitContent = true,
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().onFocusChanged { within = it.hasFocus },
        ) {
            itemsIndexed(notices, key = { _, notice -> notice.key }) { index, notice ->
                Column {
                    NoticeRow(
                        notice = notice,
                        onClick = {
                            when {
                                !notice.canOpen -> actions.dismissNotice(notice)
                                actions.openNotice(notice) -> onDismiss()
                            }
                        },
                        onLongClick = { held = notice.key },
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    )
                    if (expanded == notice.key) {
                        NoticeEntries(
                            notice = notice,
                            actions = actions,
                            // The panel goes: the bar that offers to undo is under it, at the bottom of home.
                            onMute = {
                                actions.muteApp(it)
                                onDismiss()
                            },
                            onClose = { if (held == notice.key) held = null },
                        )
                    }
                }
            }
            item(key = "clear") {
                PanelRow(stringResource(R.string.status_clear_notifications), onClick = actions.clearNotices)
            }
        }
    }
}

/** A notification: its title, under it "App · text", and at the right a note where OK has nothing to open. */
@Composable
private fun NoticeRow(notice: NoticeUi, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(13.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = scheme.surface,
            contentColor = scheme.onSurface,
            focusedContainerColor = scheme.inverseSurface,
            focusedContentColor = scheme.inverseOnSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    notice.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    line(notice.app, notice.text),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!notice.canOpen) {
                Text(
                    stringResource(R.string.notice_nothing_to_open),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * The two entries under a notification held with OK; the focus starts on the first, and leaving
 * them (▲ ▼ past either) closes them.
 */
@Composable
private fun NoticeEntries(notice: NoticeUi, actions: StatusActions, onMute: (NoticeUi) -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    // They open while OK is still held, with the focus on the first: letting go of OK is not a press of it.
    val guard = remember { FreshPressGuard() }
    // Null until the focus came in; then whether it is inside.
    var inside by remember { mutableStateOf<Boolean?>(null) }
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    // Going from one entry to the other, the focus is on neither for a moment: only out for good closes them.
    LaunchedEffect(inside) {
        if (inside == false) {
            withFrameNanos { }
            withFrameNanos { }
            close()
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = 24.dp).onPreviewKeyEvent(guard::swallow).onFocusChanged {
            if (it.hasFocus) {
                inside = true
            } else if (inside != null) {
                inside = false
            }
        },
    ) {
        PanelRow(
            stringResource(R.string.notice_dismiss),
            onClick = { actions.dismissNotice(notice) },
            modifier = Modifier.focusRequester(first),
        )
        PanelRow(stringResource(R.string.notice_mute, notice.app), onClick = { onMute(notice) })
    }
}
