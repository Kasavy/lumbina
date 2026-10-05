package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.lumbina.tv.core.designsystem.color
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.ui.Choice
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
    val forget = rememberForgetting(state.micApps.orEmpty(), commands)
    SettingsList(modifier) {
        item { Note(stringResource(R.string.access_intro)) }
        item { ConnectionRow(state, commands) }
        item { Header(stringResource(R.string.access_uses)) }
        uses(state, connected, commands)
        state.micApps?.let { micApps(it, commands, forget) }
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
private fun rememberForgetting(apps: List<MicApp>, commands: AccessCommands): Forgetting {
    val forget = remember { Forgetting(FocusRequester()) }
    val window = LocalWindowInfo.current
    LaunchedEffect(forget.confirmed) {
        val app = forget.confirmed ?: return@LaunchedEffect
        val at = apps.indexOf(app)
        val next = (apps.getOrNull(at + 1) ?: apps.getOrNull(at - 1))?.let(forget::row) ?: forget.below
        // The dialog is a window of its own: the focus can be moved once this one has it back.
        snapshotFlow { window.isWindowFocused }.first { it }
        runCatching { next.requestFocus() }
        withFrameNanos { }
        commands.forgetMicApp(app)
        forget.rows.remove(app.packageName)
        forget.confirmed = null
    }
    return forget
}

@Composable
private fun ConnectionRow(state: AccessState, commands: AccessCommands, modifier: Modifier = Modifier) {
    if (state.adb == AdbState.Connected) {
        SettingRow(
            onClick = {},
            headlineContent = { Text(stringResource(R.string.access_debugging)) },
            supportingContent = { Text(stringResource(R.string.access_connected_hint)) },
            trailingContent = { Text(stringResource(R.string.access_connected), color = Accent.Mint.color) },
            modifier = modifier,
        )
        return
    }
    val hint = when {
        state.adb == null -> R.string.access_checking
        state.connecting -> R.string.access_connecting
        state.adb == AdbState.NotAllowed -> R.string.access_not_allowed
        state.adb == AdbState.Failed -> R.string.access_failed
        else -> R.string.access_debugging_off
    }
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.access_debugging))
        Note(stringResource(hint), Modifier.padding(start = 0.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            val on = state.adb == AdbState.NotAllowed || state.adb == AdbState.Failed
            if (on) {
                OutlinedButton(onClick = commands::openDeveloperSettings) {
                    Text(stringResource(R.string.access_dev_settings))
                }
                Button(onClick = commands::connect) { Text(stringResource(R.string.access_connect)) }
            } else {
                Button(onClick = commands::openDeveloperSettings) { Text(stringResource(R.string.access_dev_settings)) }
                OutlinedButton(onClick = commands::connect) { Text(stringResource(R.string.access_connect)) }
            }
        }
    }
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
        val lumbina = state.settings.openOnPowerOn
        ChoiceRow(
            title = stringResource(R.string.access_power_on),
            options = listOf(
                Choice(stringResource(R.string.access_power_on_lumbina), chosen = lumbina) {
                    commands.setOpenOnPowerOn(true)
                },
                Choice(stringResource(R.string.access_power_on_nothing), chosen = !lumbina) {
                    commands.setOpenOnPowerOn(false)
                },
            ),
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
