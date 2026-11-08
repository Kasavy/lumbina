package dev.lumbina.tv.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.access.UserGrant

/**
 * Under a setting that is on but waits for an access only the user gives: [note] says so, and
 * [allow] opens the system's screen for it, or "Access" where the device has none (Lumbina gives
 * itself the access over debugging there). Read again whenever the page comes back, as the user
 * returns from the system's screen; [onRead] hears each reading, for a page that shows less while
 * the access is missing.
 */
@Composable
internal fun GrantNeeded(grant: UserGrant, note: String, allow: String, onRead: (Boolean) -> Unit = {}) {
    // Read as the page is first drawn (a quick question to the system): neither the note nor what
    // the page hides while it shows comes and goes on the first frame.
    var granted by remember(grant) { mutableStateOf(grant.granted()) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val read by rememberUpdatedState(onRead)
    LifecycleResumeEffect(grant) {
        val now = grant.granted()
        // Given while away: the row goes, and the focus on it would go with it. Up is the setting it was for.
        if (now && focused) focusManager.moveFocus(FocusDirection.Up)
        granted = now
        read(now)
        onPauseOrDispose {}
    }
    if (granted) return
    val links = LocalSettingsLinks.current
    Note(note)
    SettingRow(
        onClick = { if (!grant.openSettings()) links.page(SettingsPage.Access) },
        headlineContent = { Text(allow) },
        modifier = Modifier.onFocusChanged { focused = it.hasFocus },
    )
}

/** Why apps' channels can't come: no provider, or no access to it (granted on the channels' page). */
@Composable
internal fun ChannelsMissing(missing: WatchNextMissing) {
    when (missing) {
        WatchNextMissing.Provider -> Note(stringResource(R.string.channels_no_provider))

        WatchNextMissing.Access -> {
            Note(stringResource(R.string.channels_access_note))
            val links = LocalSettingsLinks.current
            SettingRow(
                onClick = { links.page(SettingsPage.Channels) },
                headlineContent = { Text(stringResource(R.string.channels_access_allow)) },
            )
        }
    }
}
