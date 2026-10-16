package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.designsystem.color
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.SetupResult
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.ui.ConfirmDialog
import kotlinx.coroutines.flow.first

/**
 * What Lumbina does over its connection to the device's debugging: whether it is connected (and
 * the way to connect), each use with its switch, and how to take the access back.
 */
@Composable
fun AccessPanel(state: AccessState?, commands: AccessCommands, modifier: Modifier = Modifier) {
    if (state == null) return
    val connected = state.adb == AdbState.Connected
    // The page's first row: where the focus goes when the row it stood on is taken away.
    val top = remember { FocusRequester() }
    var wasConnected by remember { mutableStateOf(connected) }
    LaunchedEffect(connected) {
        // "Connect" and the rows by it go once connected.
        if (connected && !wasConnected) runCatching { top.requestFocus() }
        wasConnected = connected
    }
    val forget = rememberForgetting(state.micApps.orEmpty(), commands, top)
    SettingsList(modifier) {
        item { Note(stringResource(R.string.access_intro)) }
        connection(state, commands, top)
        item { Header(stringResource(R.string.access_uses)) }
        uses(state, connected, commands)
        state.micApps?.let { micApps(it, commands, forget) }
        // With debugging off there is no access to take back, and nothing for the network to reach.
        if (state.adb != null && state.adb != AdbState.Off) {
            item { Header(stringResource(R.string.access_security)) }
            item { Note(stringResource(R.string.access_security_note)) }
            // Keyed: the row keeps the focus it is given when a row above it goes ([Forgetting]).
            item(key = TURN_OFF_ROW) {
                SettingRow(
                    onClick = commands::openDeveloperSettings,
                    headlineContent = { Text(stringResource(R.string.access_off)) },
                    supportingContent = { Text(stringResource(R.string.access_off_hint)) },
                    modifier = Modifier.focusRequester(forget.below),
                )
            }
        }
    }
    forget.asking?.let { app ->
        ConfirmDialog(
            title = stringResource(R.string.access_mic_forget_title, app.label),
            text = stringResource(R.string.access_mic_forget_text),
            confirmLabel = stringResource(R.string.access_mic_forget_confirm),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = { forget.confirm(app) },
            onDismiss = { forget.asking = null },
        )
    }
}

private const val TURN_OFF_ROW = "turn-off"

/**
 * Forgetting the answer about an app: asked about first, and since the app's row then goes, the
 * focus is moved to a row next to it beforehand (left without one, it would jump to the sections).
 */
private class Forgetting(val below: FocusRequester) {
    var asking by mutableStateOf<MicApp?>(null)
    var confirmed by mutableStateOf<MicApp?>(null)
    val rows = mutableMapOf<String, FocusRequester>()

    fun row(app: MicApp): FocusRequester = rows.getOrPut(app.packageName) { FocusRequester() }

    fun confirm(app: MicApp) {
        asking = null
        confirmed = app
    }
}

@Composable
private fun rememberForgetting(apps: List<MicApp>, commands: AccessCommands, top: FocusRequester): Forgetting {
    val forget = remember { Forgetting(FocusRequester()) }
    val window = LocalWindowInfo.current
    LaunchedEffect(forget.confirmed) {
        val app = forget.confirmed ?: return@LaunchedEffect
        val at = apps.indexOf(app)
        val next = (apps.getOrNull(at + 1) ?: apps.getOrNull(at - 1))?.let(forget::row) ?: forget.below
        // The dialog is a window of its own: the focus can be moved once this one has it back.
        snapshotFlow { window.isWindowFocused }.first { it }
        // The row below is there only with debugging on: without it, the page's first row.
        runCatching { next.requestFocus() }.onFailure { runCatching { top.requestFocus() } }
        withFrameNanos { }
        commands.forgetMicApp(app)
        forget.rows.remove(app.packageName)
        forget.confirmed = null
    }
    return forget
}

/**
 * Whether Lumbina is connected to the device's debugging, and the ways to connect while it is
 * not. The first row is there in every state, the one where that is still being found out
 * included: it is where the focus stands as the page opens, and a row that gave way to another
 * as the state came in would send the focus on to a switch below.
 */
private fun LazyListScope.connection(state: AccessState, commands: AccessCommands, top: FocusRequester) {
    val connected = state.adb == AdbState.Connected
    item(key = "connection") {
        val now = when {
            connected -> R.string.access_connected_hint
            state.adb == null -> R.string.access_checking
            else -> R.string.access_not_connected
        }
        SettingRow(
            onClick = {},
            headlineContent = { Text(stringResource(R.string.access_debugging)) },
            supportingContent = { Text(stringResource(now)) },
            trailingContent = if (connected) {
                { Text(stringResource(R.string.access_connected), color = Accent.Mint.color) }
            } else {
                null
            },
            modifier = Modifier.focusRequester(top),
        )
    }
    if (!connected && state.adb != null) ways(state, commands)
}

/** How to connect, while Lumbina is not: what the state asks for first, and what the last try came to. */
private fun LazyListScope.ways(state: AccessState, commands: AccessCommands) {
    val debuggingOn = state.adb == AdbState.NotAllowed || state.adb == AdbState.Failed
    val hint = when {
        state.connecting -> R.string.access_connecting
        state.adb == AdbState.NotAllowed -> R.string.access_not_allowed
        state.adb == AdbState.Failed -> R.string.access_failed
        else -> R.string.access_debugging_off
    }
    item(key = "connection-hint") { Note(stringResource(hint)) }
    val rows = listOf(
        "connect" to (R.string.access_connect to commands::connect),
        "developer" to (R.string.access_dev_settings to commands::openDeveloperSettings),
    )
    // What to do next comes first: turn debugging on, or, with it on, connect.
    (if (debuggingOn) rows else rows.reversed()).forEach { (key, row) ->
        item(key = key) {
            SettingRow(onClick = row.second, headlineContent = { Text(stringResource(row.first)) })
        }
    }
    // "Connect" was pressed and it did not: why, in the warning's color, or the press seems lost.
    // Not what no longer holds: "debugging is off" after it was turned on.
    val tried = state.tried.takeUnless { state.connecting || (it == SetupResult.Off && debuggingOn) } ?: return
    item(key = "tried") {
        Text(
            stringResource(tried.why),
            style = MaterialTheme.typography.bodySmall,
            color = LocalTheme.current.colorOf(ThemeRole.Warning),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

private val SetupResult.why: Int
    get() = when (this) {
        SetupResult.NotAllowed -> R.string.access_tried_not_allowed
        SetupResult.NotKept -> R.string.access_tried_not_kept
        SetupResult.AllowedOnce -> R.string.access_tried_allowed_once
        SetupResult.Failed -> R.string.access_tried_failed
        else -> R.string.access_tried_off
    }

/**
 * The apps that record the microphone themselves: each answer the user gave, to change. What the
 * list is for is said only while it is empty: one who answered a question knows.
 */
private fun LazyListScope.micApps(apps: List<MicApp>, commands: AccessCommands, forget: Forgetting) {
    item { Header(stringResource(R.string.access_mic_apps)) }
    if (apps.isEmpty()) item { Note(stringResource(R.string.access_mic_apps_note)) }
    items(apps, key = { it.packageName }) { app ->
        SwitchRow(
            title = app.label,
            hint = null,
            checked = app.allowed,
            onClick = { commands.toggleMicApp(app) },
            modifier = Modifier.focusRequester(forget.row(app)),
            onLongClick = { forget.asking = app },
            holdIcon = R.drawable.ic_delete,
        )
    }
}

private fun LazyListScope.uses(state: AccessState, connected: Boolean, commands: AccessCommands) {
    item {
        SwitchRow(
            title = stringResource(R.string.access_keep_service),
            hint = stringResource(R.string.access_keep_service_hint),
            checked = state.keepsService,
            onClick = commands::toggleKeepService,
            enabled = connected,
        )
    }
    item {
        SwitchRow(
            title = stringResource(R.string.access_close_apps),
            hint = stringResource(R.string.access_close_apps_hint),
            checked = state.settings.closeFromRecents,
            onClick = commands::toggleCloseFromRecents,
            enabled = connected,
        )
    }
    item {
        SettingRow(
            enabled = connected || state.usageGranted,
            onClick = { if (!state.usageGranted) commands.grantUsageStats() },
            headlineContent = { Text(stringResource(R.string.access_usage)) },
            supportingContent = { Text(stringResource(R.string.access_usage_hint)) },
            trailingContent = {
                val granted = state.usageGranted
                Text(stringResource(if (granted) R.string.access_usage_granted else R.string.access_usage_grant))
            },
        )
    }
}
