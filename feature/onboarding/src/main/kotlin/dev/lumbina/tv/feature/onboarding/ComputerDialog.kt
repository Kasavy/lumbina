package dev.lumbina.tv.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.lumbina.tv.core.ui.CommandsDialog

/**
 * The way from a computer: the adb commands with this device's address and services filled in,
 * and the same text as a QR code, so a phone can pass it on.
 */
@Composable
internal fun ComputerDialog(commands: ComputerCommands, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    CommandsDialog(
        title = stringResource(R.string.computer_title),
        text = stringResource(R.string.computer_text),
        lines = commands.lines,
        onDismiss = onDismiss,
        modifier = modifier,
        note = stringResource(R.string.computer_no_address).takeIf { !commands.addressKnown },
    )
}
