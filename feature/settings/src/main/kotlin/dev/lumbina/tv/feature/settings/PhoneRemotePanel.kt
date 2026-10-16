package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.QrCode

/** Switch on the phone remote page, and the QR code that opens it. */
@Composable
fun PhoneRemotePanel(
    state: PhoneRemoteState?,
    onToggle: () -> Unit,
    onNewKey: () -> Unit,
    onToggleScreensaver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    SettingsColumn(modifier) {
        SwitchRow(
            title = stringResource(R.string.phone_enabled),
            hint = stringResource(R.string.phone_enabled_hint),
            checked = state.enabled,
            onClick = onToggle,
        )
        if (state.enabled) {
            val address = state.address
            if (address == null) {
                Note(stringResource(R.string.phone_no_network))
            } else {
                // One under another: the panel is too narrow for the code and its text side by side.
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                ) {
                    // The code first: it is what the page is opened for, and must be whole without scrolling.
                    QrCode(address, size = 150.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Text(stringResource(R.string.phone_scan), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        address,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.phone_can),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.phone_on_screensaver),
                hint = stringResource(R.string.phone_on_screensaver_hint),
                checked = state.onScreensaver,
                onClick = onToggleScreensaver,
            )
            SettingRow(
                onClick = onNewKey,
                headlineContent = { Text(stringResource(R.string.phone_new_key)) },
                supportingContent = { Text(stringResource(R.string.phone_new_key_hint)) },
            )
        }
    }
}
