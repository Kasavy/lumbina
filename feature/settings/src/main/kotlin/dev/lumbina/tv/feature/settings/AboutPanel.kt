package dev.lumbina.tv.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
fun AboutPanel(system: SystemFacts, deviceProfile: String, modifier: Modifier = Modifier) {
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
        item {
            Fact(stringResource(R.string.about_version), system.appVersion + if (system.debugBuild) " (debug)" else "")
        }
        item { Fact(stringResource(R.string.diag_profile), deviceProfile) }
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
