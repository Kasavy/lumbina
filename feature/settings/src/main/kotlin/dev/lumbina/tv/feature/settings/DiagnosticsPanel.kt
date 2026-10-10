package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.device.FirmwarePart
import dev.lumbina.tv.core.domain.device.TurnedOn
import dev.lumbina.tv.core.domain.diagnostics.DiagnosticsSnapshot

/** What the service, the mic and the speech models are doing, the recent log, and a report to save. */
@Composable
fun DiagnosticsPanel(
    state: DiagnosticsState?,
    saved: SavedReport,
    parts: PartsState,
    onTurnOn: (FirmwarePart) -> Unit,
    openAccess: (() -> Unit)?,
    onSaveReport: () -> Unit,
    onNotificationAccess: () -> Unit,
    onUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    SettingsList(modifier, spacing = 2.dp) {
        facts(state.snapshot)
        parts(parts, state.snapshot.partsOff, onTurnOn, openAccess)
        item { Header(stringResource(R.string.diag_permissions)) }
        item {
            Grant(
                R.string.diag_notifications,
                R.string.diag_notifications_hint,
                state.notifications,
                onNotificationAccess,
            )
        }
        item { Grant(R.string.diag_usage, R.string.diag_usage_hint, state.usage, onUsageAccess) }
        item {
            // Stays enabled while a report is saved (presses are ignored then): a disabled button drops the focus.
            Button(onClick = onSaveReport, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.diag_save_report))
            }
        }
        item { Note(stringResource(if (saved.saving) R.string.diag_saving else R.string.diag_report_holds)) }
        saved.path?.let { path -> item { Note(stringResource(R.string.diag_saved, path)) } }
        if (saved.failed) item { Note(stringResource(R.string.diag_save_failed)) }
        item { Header(stringResource(R.string.diag_log)) }
        items(state.log.asReversed()) { line ->
            // Focusable so the D-pad can scroll through the log.
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.focusable().padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

private fun LazyListScope.facts(snapshot: DiagnosticsSnapshot) {
    item { Header(stringResource(R.string.page_state)) }
    item {
        Fact(
            label = stringResource(R.string.diag_service),
            value = stringResource(if (snapshot.serviceRunning) R.string.diag_running else R.string.diag_not_running),
            problem = !snapshot.serviceRunning,
        )
    }
    // The part's own row says it is turned off: "supported" above it would only contradict.
    if (snapshot.partsOff.none { it.serves == FirmwarePart.Feature.RemoteMic }) {
        item {
            val mic = if (snapshot.remoteMic) R.string.diag_supported else R.string.diag_not_supported
            Fact(label = stringResource(R.string.diag_remote_mic), value = stringResource(mic))
        }
    }
    item {
        val models = snapshot.installedModels.joinToString().ifEmpty { stringResource(R.string.diag_none) }
        Fact(
            label = stringResource(R.string.diag_models),
            value = models,
            problem = snapshot.remoteMic && snapshot.installedModels.isEmpty(),
        )
    }
}

/**
 * The parts of the firmware that are turned off: what is lost with each, and a way to turn it on
 * where there is one. A part turned on here keeps its row, which now says so: a row that went away
 * under the focus would send the focus off the page.
 */
private fun LazyListScope.parts(
    state: PartsState,
    off: List<FirmwarePart>,
    onTurnOn: (FirmwarePart) -> Unit,
    openAccess: (() -> Unit)?,
) {
    items(state.rows(off), key = { it.part.packageName }) { row ->
        PartRow(row, state.canTurnOn, working = state.working == row.part, onTurnOn)
    }
    when (state.failure) {
        TurnedOn.Refused -> item { Note(stringResource(R.string.diag_part_refused)) }

        // The access page is where the connection is made; it is there wherever a part can be turned on.
        TurnedOn.NotConnected -> if (openAccess != null) {
            item {
                SettingRow(
                    onClick = openAccess,
                    headlineContent = { Text(stringResource(R.string.diag_part_no_connection)) },
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun PartRow(row: PartRow, canTurnOn: Boolean, working: Boolean, onTurnOn: (FirmwarePart) -> Unit) {
    val title = when (row.part.serves) {
        FirmwarePart.Feature.RemoteMic -> if (row.isOff) R.string.diag_part_mic else R.string.diag_part_mic_on
        FirmwarePart.Feature.Volume -> if (row.isOff) R.string.diag_part_volume else R.string.diag_part_volume_on
    }
    val offers = row.isOff && canTurnOn
    var focused by remember { mutableStateOf(false) }
    SettingRow(
        onClick = { if (offers) onTurnOn(row.part) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            when {
                !row.isOff -> Text(stringResource(R.string.diag_part_on))

                else -> {
                    val hint = if (canTurnOn) R.string.diag_part_off else R.string.diag_part_off_no_adb
                    Text(stringResource(hint, row.part.packageName), color = problemColor(focused))
                }
            }
        },
        trailingContent = {
            // The command takes seconds: the row says it is at work.
            if (offers) Text(stringResource(if (working) R.string.diag_part_turning_on else R.string.diag_part_turn_on))
        },
        modifier = Modifier.onFocusChanged { focused = it.hasFocus },
    )
}

/** A special access the user allows in the system's settings: what it is for, and a way there until it is allowed. */
@Composable
private fun Grant(title: Int, hint: Int, granted: Boolean, onOpen: () -> Unit) {
    SettingRow(
        onClick = onOpen,
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(hint)) },
        trailingContent = {
            Text(stringResource(if (granted) R.string.diag_granted else R.string.diag_open_settings))
        },
    )
}
