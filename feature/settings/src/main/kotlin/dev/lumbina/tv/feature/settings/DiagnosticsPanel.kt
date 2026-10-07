package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.diagnostics.DiagnosticsSnapshot

/** What the service, the mic and the speech models are doing, the recent log, and a report to save. */
@Composable
fun DiagnosticsPanel(
    state: DiagnosticsState?,
    saved: SavedReport,
    onSaveReport: () -> Unit,
    onNotificationAccess: () -> Unit,
    onUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    SettingsList(modifier, spacing = 2.dp) {
        facts(state.snapshot)
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
    item {
        Fact(
            label = stringResource(R.string.diag_remote_mic),
            value = stringResource(if (snapshot.remoteMic) R.string.diag_supported else R.string.diag_not_supported),
        )
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
