package dev.lumbina.tv.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.diagnostics.SystemFacts
import dev.lumbina.tv.core.ui.MenuDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What Lumbina is (version, device profile), what it runs on, and whose work and data it carries. */
@Composable
fun AboutPanel(
    system: SystemFacts,
    deviceProfile: String,
    modifier: Modifier = Modifier,
    setup: SetupOffer? = null,
    update: UpdateUi? = null,
    onUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    val size = { bytes: Long -> Formatter.formatShortFileSize(context, bytes) }
    var showing by remember { mutableStateOf(false) }
    val components by produceState(emptyList<Component>()) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(NOTICE_ASSET).bufferedReader().use { it.readText() } }
                .map(::thirdPartyComponents)
                .getOrDefault(emptyList())
        }
    }
    SettingsList(modifier, spacing = 2.dp) {
        item { Header(stringResource(R.string.about_lumbina)) }
        item { Fact(stringResource(R.string.about_version), versionText(system)) }
        item { Fact(stringResource(R.string.diag_profile), deviceProfile) }
        if (update != null) item { UpdateRow(update, size, onUpdate) }
        if (setup != null) item { ShowSetupRow(setup) }
        item { Header(stringResource(R.string.about_device)) }
        item { Fact(stringResource(R.string.about_model), "${system.manufacturer} ${system.model}") }
        item { Fact(stringResource(R.string.diag_android), "${system.androidRelease} (API ${system.sdkInt})") }
        item {
            Fact(
                stringResource(R.string.diag_ram),
                stringResource(R.string.diag_free_of, size(system.availableRamBytes), size(system.totalRamBytes)),
            )
        }
        item {
            Fact(
                stringResource(R.string.diag_storage),
                stringResource(R.string.diag_free, size(system.freeStorageBytes)),
            )
        }
        item { Header(stringResource(R.string.about_licenses)) }
        item { Fact(stringResource(R.string.about_lumbina), stringResource(R.string.about_license_gpl)) }
        item { Fact(stringResource(R.string.about_weather), stringResource(R.string.about_weather_credit)) }
        if (components.isNotEmpty()) {
            item {
                SettingRow(
                    onClick = { showing = true },
                    headlineContent = { Text(stringResource(R.string.about_components)) },
                    supportingContent = { Text(stringResource(R.string.about_components_hint)) },
                )
            }
        }
    }
    if (showing) {
        MenuDialog(title = stringResource(R.string.about_components), onDismiss = { showing = false }) {
            items(components) { component ->
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = { Text(component.name) },
                    supportingContent = { Text(component.license) },
                )
            }
        }
    }
}

/**
 * Checking for a new version and installing it, in one row: its second line says where things
 * stand, and its name says what OK does ([action]): check, download and install, try again, stop
 * waiting for the system's question, or nothing while something is under way.
 */
@Composable
private fun UpdateRow(update: UpdateUi, size: (Long) -> String, onUpdate: () -> Unit) {
    val status = updateStatus(update, size)
    val name = when (update.action()) {
        UpdateAction.Check -> R.string.update_check
        is UpdateAction.Install -> R.string.update_install
        is UpdateAction.StopWaiting -> R.string.update_stop_waiting
        UpdateAction.None -> R.string.update_title
    }
    SettingRow(
        onClick = onUpdate,
        headlineContent = { Text(stringResource(name)) },
        supportingContent = status?.let { (text, problem) ->
            { Text(text, color = if (problem) rowWarning() else Color.Unspecified) }
        },
    )
}

/** What the update row says under its name, and whether that is a problem; null before the first check. */
@Composable
private fun updateStatus(update: UpdateUi, size: (Long) -> String): Pair<String, Boolean>? = when (update) {
    UpdateUi.Idle -> null

    UpdateUi.Checking -> stringResource(R.string.update_checking) to false

    UpdateUi.UpToDate -> stringResource(R.string.update_latest) to false

    UpdateUi.CheckFailed -> stringResource(R.string.update_check_failed) to true

    is UpdateUi.Available ->
        stringResource(R.string.update_available, update.release.version, size(update.release.bytes)) to false

    is UpdateUi.Downloading -> stringResource(R.string.update_downloading, update.percent) to false

    is UpdateUi.Broken ->
        stringResource(if (update.corrupt) R.string.update_corrupt else R.string.update_download_failed) to true

    is UpdateUi.Installing -> stringResource(R.string.update_installing) to false

    UpdateUi.InstallingItself -> stringResource(R.string.update_installing_itself) to false

    is UpdateUi.NotInstalled -> stringResource(R.string.update_not_installed) to true

    UpdateUi.Refused -> stringResource(R.string.update_refused) to true

    is UpdateUi.Older -> stringResource(R.string.update_older, update.release.version) to true
}

/** Lumbina's version, marked when the build is a debug one. */
@Composable
private fun versionText(system: SystemFacts): String =
    if (system.debugBuild) stringResource(R.string.about_version_debug, system.appVersion) else system.appVersion

/** Opens the first-run setup again, at the steps that are left. */
@Composable
private fun ShowSetupRow(setup: SetupOffer) {
    SettingRow(
        onClick = setup.show,
        headlineContent = { Text(stringResource(R.string.about_show_setup)) },
        supportingContent = {
            Text(stringResource(if (setup.hidden) R.string.about_show_setup_hint else R.string.about_show_setup_done))
        },
    )
}

/**
 * The first-run setup is over: a way to open it again. The row stays once pressed ([hidden] no
 * more), or the focus on it would have nowhere to be.
 */
class SetupOffer(val hidden: Boolean, val show: () -> Unit)
