package dev.lumbina.tv.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.sleep.AUTO_SLEEP_MINUTES
import dev.lumbina.tv.core.domain.sleep.SCREENSAVER_MINUTES
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.CommandsDialog

/**
 * Auto-sleep and the screensaver. When Lumbina may not set the system screensaver, [openAccess]
 * (null where this device has no Access page) leads to the access that lets it.
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
    SettingsColumn(modifier) {
        Header(stringResource(R.string.sleep_header))
        ChoiceRow(
            title = stringResource(R.string.sleep_auto),
            hint = stringResource(R.string.sleep_auto_hint),
            options = AUTO_SLEEP_MINUTES.map { minutes ->
                Choice(autoSleepLabel(minutes), chosen = minutes == state.sleep.autoSleepMinutes) {
                    commands.setAutoSleep(minutes)
                }
            },
        )
        Note(stringResource(R.string.sleep_timer_note))

        Header(stringResource(R.string.screensaver_header))
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
        if (state.screensaverDenied) ScreensaverDenied(openAccess, computerCommands)
        SwitchRow(
            title = stringResource(R.string.screensaver_online_photos),
            hint = stringResource(R.string.screensaver_online_photos_hint),
            checked = state.sleep.onlinePhotos,
            onClick = commands::toggleOnlinePhotos,
        )
        SettingRow(
            onClick = onPreviewScreensaver,
            headlineContent = { Text(stringResource(R.string.screensaver_preview)) },
        )
    }
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
