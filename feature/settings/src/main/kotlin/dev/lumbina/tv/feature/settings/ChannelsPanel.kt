package dev.lumbina.tv.feature.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.access.PermissionAnswer

/**
 * The channels apps published, each shown as a tab under Recommendations or hidden. Where the
 * system's TV Provider keeps other apps' rows behind a permission, a button asks for it again;
 * [onPermission] hears the answer.
 */
@Composable
fun ChannelsPanel(
    state: ChannelsPanelState?,
    onToggleChannel: (String) -> Unit,
    onPermission: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) return
    val ask = rememberPermissionAsk(onPermission)
    SettingsList(modifier) {
        if (!state.available) {
            item { Header(stringResource(R.string.channels_header)) }
            item { Note(stringResource(R.string.channels_no_provider)) }
            return@SettingsList
        }
        state.missingPermission?.let { permission ->
            item { Header(stringResource(R.string.channels_access_header)) }
            item { Note(stringResource(R.string.channels_access_note)) }
            item {
                SettingRow(
                    onClick = { ask(permission) },
                    headlineContent = { Text(stringResource(R.string.channels_access_allow)) },
                )
            }
        }
        item { Header(stringResource(R.string.channels_header)) }
        if (state.channels.isEmpty()) item { Note(stringResource(R.string.channels_none)) }
        items(state.channels, key = { it.key }) { channel ->
            SettingRow(
                onClick = { onToggleChannel(channel.key) },
                headlineContent = { Text(channel.name) },
                supportingContent = {
                    Text(
                        "${channel.app} · " +
                            pluralStringResource(R.plurals.channels_programs, channel.programs, channel.programs),
                    )
                },
                trailingContent = { Switch(checked = channel.shown, onCheckedChange = null) },
            )
        }
        item { Note(stringResource(R.string.channels_publish_hint)) }
    }
}

/**
 * Asks for a permission. When the system refuses without showing its dialog (the user chose not
 * to be asked again, see [PermissionAnswer]), the app's page in the system settings opens, where
 * it can still be allowed; a "no" said in the dialog just now is respected.
 */
@Composable
private fun rememberPermissionAsk(onAnswer: (Boolean) -> Unit): (String) -> Unit {
    val activity = LocalActivity.current
    var asked by remember { mutableStateOf<PermissionAsk?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val ask = asked ?: return@rememberLauncherForActivityResult
        asked = null
        val answer = PermissionAnswer.of(
            granted = granted,
            rationaleBefore = ask.rationale,
            rationaleAfter = activity?.shouldShowRequestPermissionRationale(ask.permission) == true,
            millis = SystemClock.elapsedRealtime() - ask.at,
        )
        if (answer == PermissionAnswer.DeniedForGood && !ask.rationale && activity != null) openAppSettings(activity)
        onAnswer(granted)
    }
    return { permission ->
        val rationale = activity?.shouldShowRequestPermissionRationale(permission) == true
        asked = PermissionAsk(permission, rationale, SystemClock.elapsedRealtime())
        launcher.launch(permission)
    }
}

/** A request in flight: what was asked, the rationale before it, and when. */
private class PermissionAsk(val permission: String, val rationale: Boolean, val at: Long)

private fun openAppSettings(activity: Activity) {
    val page = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", activity.packageName, null))
    try {
        activity.startActivity(page)
    } catch (_: ActivityNotFoundException) {
        // No such page on this firmware: nothing more to offer.
    }
}
