package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.backdrop.ScreensaverPicker
import dev.lumbina.tv.core.ui.CommandsDialog

// The rows of the screensaver page that make Lumbina's screensaver the system's.

/**
 * The ways to make Lumbina the system screensaver when it may not do so itself: the device's own
 * list first (no adb), then Lumbina's own access, whose page also has the commands for a computer
 * where the device won't let Lumbina connect. Without that page (an edition without adb), the
 * commands here.
 */
internal fun LazyListScope.screensaverWays(
    picker: ScreensaverPicker?,
    commands: SleepSettingsCommands,
    openAccess: (() -> Unit)?,
    computerCommands: List<String>,
    modifier: Modifier,
) {
    picker?.let { item { ScreensaverPickerRow(it, commands::openScreensaverPicker, modifier) } }
    if (openAccess != null) {
        item { ScreensaverNeedsAccess(openAccess, modifier) }
    } else {
        item { ScreensaverFromComputer(computerCommands, modifier) }
    }
}

/**
 * Where the focus goes when the ways to the system screensaver go (it was chosen meanwhile): back
 * to the switch above them if it stood on one of them, rather than to whatever row the list picks.
 * Only a move while they show counts as leaving them: the list's own move as they go doesn't.
 */
internal class WaysFocus(val toggle: FocusRequester) {
    var shown = false
    var onWayRow by mutableStateOf(false)
    val onWay: Modifier = Modifier.onFocusChanged { if (it.isFocused) onWayRow = true }
    val offWay: Modifier = Modifier.onFocusChanged { if (it.isFocused && shown) onWayRow = false }
}

@Composable
internal fun rememberWaysFocus(shown: Boolean): WaysFocus {
    val focus = remember { WaysFocus(FocusRequester()) }
    SideEffect { focus.shown = shown }
    LaunchedEffect(shown) {
        if (!shown && focus.onWayRow) {
            focus.onWayRow = false
            runCatching { focus.toggle.requestFocus() }
        }
    }
    return focus
}

/** Whether Lumbina's screensaver is the system's, and the way to make it so. */
@Composable
internal fun ScreensaverToggleRow(isLumbina: Boolean, onClick: () -> Unit, focus: WaysFocus) {
    SettingRow(
        onClick = onClick,
        headlineContent = { Text(stringResource(R.string.screensaver_use_lumbina)) },
        supportingContent = {
            val hint = if (isLumbina) R.string.screensaver_is_lumbina else R.string.screensaver_not_lumbina
            Text(stringResource(hint))
        },
        modifier = Modifier.focusRequester(focus.toggle).then(focus.offWay),
    )
}

/** The device's own list of screensavers, where the user chooses Lumbina's with no access needed. */
@Composable
private fun ScreensaverPickerRow(picker: ScreensaverPicker, open: () -> Unit, modifier: Modifier = Modifier) {
    val hint = when (picker) {
        ScreensaverPicker.Screen -> R.string.screensaver_pick_screen_hint
        ScreensaverPicker.InSettings -> R.string.screensaver_pick_in_settings_hint
    }
    SettingRow(
        onClick = open,
        headlineContent = { Text(stringResource(R.string.screensaver_pick)) },
        supportingContent = { Text(stringResource(hint)) },
        modifier = modifier,
    )
}

/** Lumbina may not set the system screensaver: the way to the access that lets it. */
@Composable
private fun ScreensaverNeedsAccess(openAccess: () -> Unit, modifier: Modifier = Modifier) {
    SettingRow(
        onClick = openAccess,
        headlineContent = { Text(stringResource(R.string.screensaver_needs_access)) },
        modifier = modifier,
    )
}

/** The commands that make Lumbina the system screensaver from a computer, for a device that won't let it connect. */
@Composable
private fun ScreensaverFromComputer(computerCommands: List<String>, modifier: Modifier = Modifier) {
    var showing by remember { mutableStateOf(false) }
    SettingRow(
        onClick = { showing = true },
        headlineContent = { Text(stringResource(R.string.screensaver_from_computer)) },
        supportingContent = { Text(stringResource(R.string.screensaver_denied)) },
        modifier = modifier,
    )
    if (showing) {
        CommandsDialog(
            title = stringResource(R.string.screensaver_from_computer).removeSuffix("…"),
            text = stringResource(R.string.computer_commands_text),
            lines = computerCommands,
            onDismiss = { showing = false },
        )
    }
}
