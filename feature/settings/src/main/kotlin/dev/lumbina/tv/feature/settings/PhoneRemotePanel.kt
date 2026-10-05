package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 32.dp, top = 12.dp, bottom = 12.dp),
                ) {
                    QrCode(address, size = 200.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.phone_scan), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            address,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.phone_can),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SettingRow(
                onClick = onNewKey,
                headlineContent = { Text(stringResource(R.string.phone_new_key)) },
                supportingContent = { Text(stringResource(R.string.phone_new_key_hint)) },
            )
        }
    }
}
