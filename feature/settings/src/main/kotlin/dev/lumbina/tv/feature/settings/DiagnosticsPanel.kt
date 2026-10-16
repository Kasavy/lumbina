package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.designsystem.LocalTheme
import dev.lumbina.tv.core.designsystem.colorOf
import dev.lumbina.tv.core.domain.device.FirmwarePart
import dev.lumbina.tv.core.domain.device.TurnedOn
import dev.lumbina.tv.core.domain.theme.ThemeRole
import dev.lumbina.tv.core.ui.QrCode
import dev.lumbina.tv.core.ui.TICK

/** A page inside diagnostics. */
private enum class Inner { Report, Log }

/**
 * How things stand, a row each with a mark: a tick, or an exclamation mark and what is wrong, and
 * then OK leads to where it is put right. Under them the report and the log, on pages of their own.
 */
@Composable
fun DiagnosticsPanel(
    state: DiagnosticsState?,
    saved: SavedReport,
    phone: PhoneRemoteState?,
    parts: PartsState,
    onTurnOn: (FirmwarePart) -> Unit,
    openAccess: (() -> Unit)?,
    onSaveReport: () -> Unit,
    onNotificationAccess: () -> Unit,
    onUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    var inner by rememberSaveable { mutableStateOf<Inner?>(null) }
    // The row left for an inner page: it takes the focus back.
    var left by remember { mutableStateOf<Inner?>(null) }
    val list = rememberLazyListState()
    val hold = LocalParkFocus.current
    val back = {
        left = inner
        inner = null
    }
    when (inner) {
        Inner.Report -> InnerPage(stringResource(R.string.diag_report), back) {
            ReportPage(saved, phone, onSaveReport)
        }

        Inner.Log -> {
            // An empty log is one row to stand on; a log with lines is turned, not chosen from.
            val keys = stringResource(R.string.diag_log_keys).takeIf { state.log.isNotEmpty() }
            InnerPage(stringResource(R.string.diag_log_title), back, keys) { LogPage(state.log) }
        }

        null -> SettingsList(modifier, spacing = 2.dp, edges = list) {
            states(state, onNotificationAccess, onUsageAccess)
            parts(parts, state.snapshot.partsOff, onTurnOn, openAccess)
            item { GroupLine() }
            Inner.entries.forEach { page ->
                item(key = page.name) {
                    val title = if (page == Inner.Report) R.string.diag_report else R.string.diag_log_title
                    SettingRow(
                        onClick = {
                            hold()
                            inner = page
                        },
                        headlineContent = { Text(stringResource(title)) },
                        modifier = Modifier.focusedOnReturn(left == page) { left = null },
                    )
                }
            }
        }
    }
}

/** The states this device has: the service, the mic and the speech models where there is a mic, the two accesses. */
private fun LazyListScope.states(state: DiagnosticsState, onNotificationAccess: () -> Unit, onUsageAccess: () -> Unit) {
    val snapshot = state.snapshot
    item {
        val links = LocalSettingsLinks.current
        StateRow(
            title = stringResource(R.string.diag_service),
            problem = stringResource(R.string.diag_service_off).takeIf { !snapshot.serviceRunning },
            onFix = links.setup,
        )
    }
    // A part of the firmware turned off has a row of its own below, which says so.
    val micOff = snapshot.partsOff.any { it.serves == FirmwarePart.Feature.RemoteMic }
    if (snapshot.remoteMic && !micOff) {
        item { StateRow(stringResource(R.string.diag_remote_mic), problem = null) }
    }
    if (snapshot.remoteMic) {
        item {
            val links = LocalSettingsLinks.current
            StateRow(
                title = stringResource(R.string.diag_models),
                problem = stringResource(R.string.diag_models_none).takeIf { snapshot.installedModels.isEmpty() },
                onFix = { links.page(SettingsPage.Voice) },
            )
        }
    }
    item {
        val none = stringResource(R.string.diag_no_access).takeIf { !state.notifications }
        StateRow(stringResource(R.string.diag_notifications), none, onNotificationAccess)
    }
    item {
        val none = stringResource(R.string.diag_no_access).takeIf { !state.usage }
        StateRow(stringResource(R.string.diag_usage), none, onUsageAccess)
    }
}

/**
 * One state: in order, a tick and its name, and OK does nothing; not in order, an exclamation
 * mark, what is wrong in the warning color, and OK goes to [onFix].
 */
@Composable
private fun StateRow(title: String, problem: String?, onFix: () -> Unit = {}) {
    SettingRow(
        onClick = { if (problem != null) onFix() },
        leadingContent = {
            Text(
                text = if (problem == null) TICK else "!",
                style = MaterialTheme.typography.titleLarge,
                color = if (problem == null) MaterialTheme.colorScheme.primary else rowWarning(),
                modifier = Modifier.width(24.dp),
            )
        },
        headlineContent = { Text(title) },
        supportingContent = problem?.let { { Text(it, color = rowWarning()) } },
    )
}

/** Saving a report for whoever helps: what it holds, where it went, or why it did not. */
@Composable
private fun ReportPage(saved: SavedReport, phone: PhoneRemoteState?, onSave: () -> Unit) {
    SettingsColumn {
        // Stays pressable while a report is saved (presses are ignored then): a dead row would drop the focus.
        SettingRow(
            onClick = onSave,
            headlineContent = { Text(stringResource(R.string.diag_save_report)) },
            supportingContent = saved.path?.let { { Text(stringResource(R.string.diag_saved, it)) } },
        )
        Note(stringResource(if (saved.saving) R.string.diag_saving else R.string.diag_report_holds))
        if (saved.failed) {
            Text(
                stringResource(R.string.diag_save_failed),
                color = LocalTheme.current.colorOf(ThemeRole.Warning),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        // A saved report can go to a phone: the phone remote page's server hands it out.
        if (saved.path != null) {
            val phoneAddress = phone?.reportAddress
            if (phoneAddress == null) {
                // The page off, or on with no network to reach it by.
                val on = phone?.enabled == true
                val why = if (on) R.string.diag_report_no_network else R.string.diag_report_phone_off
                Note(stringResource(why))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 12.dp),
                ) {
                    // The words first: the page has one row and does not scroll, and the code must be whole.
                    Text(stringResource(R.string.diag_report_phone), style = MaterialTheme.typography.bodySmall)
                    QrCode(phoneAddress, size = 140.dp)
                }
            }
        }
    }
}

/** The last lines of Lumbina's log, the newest first. */
@Composable
private fun LogPage(log: List<String>) {
    if (log.isEmpty()) {
        // Something for the focus to stand on.
        SettingRow(onClick = {}, headlineContent = { Text(stringResource(R.string.diag_log_empty)) })
        return
    }
    val list = rememberLazyListState()
    // The page itself has the focus and ▲ ▼ turn it: a focus going line by line, unseen on plain
    // text, would move nothing until it reached the edge.
    val turned = Modifier.onPreviewKeyEvent { event ->
        val by = when (event.key) {
            Key.DirectionDown -> LOG_STEP
            Key.DirectionUp -> -LOG_STEP
            else -> return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) list.dispatchRawDelta(by * list.layoutInfo.viewportSize.height)
        true
    }
    SettingsList(turned.focusable(), spacing = 2.dp, edges = list) {
        items(log.asReversed()) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

/** Of the page's height: what one press of ▲ or ▼ turns the log by. */
private const val LOG_STEP = 0.4f

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
    SettingRow(
        onClick = { if (offers) onTurnOn(row.part) },
        headlineContent = { Text(stringResource(title)) },
        supportingContent = {
            when {
                !row.isOff -> Text(stringResource(R.string.diag_part_on))

                else -> {
                    val hint = if (canTurnOn) R.string.diag_part_off else R.string.diag_part_off_no_adb
                    Text(stringResource(hint, row.part.packageName), color = rowProblem())
                }
            }
        },
        trailingContent = {
            // The command takes seconds: the row says it is at work.
            if (offers) Text(stringResource(if (working) R.string.diag_part_turning_on else R.string.diag_part_turn_on))
        },
    )
}
