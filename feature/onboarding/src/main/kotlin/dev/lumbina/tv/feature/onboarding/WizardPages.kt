@file:Suppress("ViewModelForwarding") // the pages call the view model's commands

package dev.lumbina.tv.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.serviceConsentText
import java.util.Locale

/**
 * Before anything else, on a device whose firmware was made for another market: there the device's
 * language says nothing about who is in front of it. Two buttons, each in its own language.
 */
@Composable
internal fun LanguagePage(viewModel: SetupViewModel, next: () -> Unit, onRemake: () -> Unit) {
    val russian = remember { FocusRequester() }
    val english = remember { FocusRequester() }
    val device = LocalConfiguration.current.locales[0].language
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { (if (device == RUSSIAN) russian else english).requestFocus() }
    }
    val choose = { tag: String ->
        // Lumbina had no language of its own yet: any choice makes the screen anew, and [onRemake]
        // keeps the next page from showing before, in the language it is leaving.
        onRemake()
        viewModel.setLanguage(tag)
        next()
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.wizard_language_title), style = MaterialTheme.typography.displaySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { choose(RUSSIAN) }, modifier = Modifier.focusRequester(russian)) {
                Text(ownName(RUSSIAN), style = MaterialTheme.typography.titleLarge)
            }
            Button(onClick = { choose(ENGLISH) }, modifier = Modifier.focusRequester(english)) {
                Text(ownName(ENGLISH), style = MaterialTheme.typography.titleLarge)
            }
        }
        listOf(R.string.wizard_language_note_ru, R.string.wizard_language_note_en).forEach { note ->
            Text(
                stringResource(note),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A language's name in that language itself. */
private fun ownName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

@Composable
internal fun WelcomePage(viewModel: SetupViewModel, next: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    // Read anew after a choice: the screen is built again in the new language.
    val language = viewModel.language()
    WizardPage(
        title = stringResource(R.string.wizard_welcome_title),
        text = stringResource(R.string.wizard_welcome_text),
    ) { focus ->
        Button(onClick = next, modifier = focus) { Text(stringResource(R.string.wizard_start)) }
        if (viewModel.languageSupported) {
            // A language is named in itself: whoever can't read this screen still finds theirs.
            val name = language?.let(::ownName) ?: stringResource(R.string.wizard_language_system)
            OutlinedButton(onClick = { choosing = true }) { Text(stringResource(R.string.wizard_language, name)) }
        }
    }
    if (choosing) {
        ChoiceDialog(
            title = stringResource(R.string.wizard_language_title),
            choices = AppLanguage.CHOICES.map { tag ->
                val name = tag?.let(::ownName) ?: stringResource(R.string.wizard_language_system)
                Choice(name, chosen = tag == language) { viewModel.setLanguage(tag) }
            },
            onDismiss = { choosing = false },
        )
    }
}

/**
 * The remote's service. What it sees and does is said on the page itself, so the button is named
 * for what it does: "Open settings", "Connect". How it went is said under the facts.
 */
@Composable
internal fun ServicePage(status: SetupStatus, viewModel: SetupViewModel, next: () -> Unit) {
    NextWhen(status.serviceRunning, next)
    var computer by remember { mutableStateOf(false) }
    val busy = status.connect == ConnectResult.Connecting || status.connect == ConnectResult.TurningOn
    val failed = status.connect in listOf(
        ConnectResult.NotAllowed,
        ConnectResult.DebuggingOff,
        ConnectResult.NotKept,
        ConnectResult.AllowedOnce,
        ConnectResult.Failed,
    )
    WizardPage(
        title = stringResource(R.string.setup_home_title),
        text = if (status.serviceRunning) stringResource(R.string.wizard_home_done) else serviceText(status),
        facts = serviceConsentText(viewModel.serviceScope).takeUnless { status.serviceRunning }.orEmpty(),
        problem = failed,
        stage = Triple(status.serviceRunning, status.connect, status.debuggingOn),
    ) { focus ->
        if (status.serviceRunning) return@WizardPage DoneButton(next, focus)
        when {
            status.path == ServicePath.Computer ->
                Button(onClick = { computer = true }, modifier = focus) { Text(stringResource(R.string.setup_how)) }

            // Enabled, doing nothing: a disabled button drops the focus, which would land on "Skip setup".
            busy -> Button(onClick = {}, modifier = focus) {
                Text(stringResource(R.string.wizard_connecting))
            }

            status.path == ServicePath.AccessibilitySettings ||
                (status.connect == ConnectResult.NotKept && viewModel.hasAccessibilitySettings) ->
                Button(onClick = viewModel::openAccessibilitySettings, modifier = focus) {
                    Text(stringResource(R.string.setup_open_settings))
                }

            !status.debuggingOn -> Button(onClick = viewModel::openDeveloperSettings, modifier = focus) {
                Text(stringResource(R.string.setup_dev_settings))
            }

            else -> Button(onClick = viewModel::connect, modifier = focus) {
                Text(stringResource(R.string.setup_connect))
            }
        }
        OutlinedButton(onClick = next) { Text(stringResource(R.string.wizard_later)) }
    }
    val packageName = LocalContext.current.packageName
    val commands by produceState<ComputerCommands?>(null, computer) {
        value = if (computer) viewModel.computerCommands(packageName) else null
    }
    commands?.let { ComputerDialog(it, onDismiss = { computer = false }) }
}

/** What to do on this device to turn the service on, or how the last try went. */
@Composable
private fun serviceText(status: SetupStatus): String = stringResource(
    when (status.path) {
        ServicePath.AccessibilitySettings -> R.string.setup_home_settings

        ServicePath.Computer -> R.string.setup_home_computer

        ServicePath.LocalAdb -> when (status.connect) {
            ConnectResult.Connecting -> R.string.setup_home_connecting
            ConnectResult.NotAllowed -> R.string.setup_home_not_allowed
            ConnectResult.DebuggingOff -> R.string.setup_home_debugging_off
            ConnectResult.NotKept -> R.string.setup_home_not_kept
            ConnectResult.AllowedOnce -> R.string.setup_home_allowed_once
            ConnectResult.TurningOn -> R.string.setup_home_turning_on
            ConnectResult.Failed -> R.string.setup_home_failed
            null -> if (status.debuggingOn) R.string.setup_home_adb_on else R.string.setup_home_adb
        }
    },
)

/** Apps' channels and "continue watching": says what the device is about to ask, then lets it ask. */
@Composable
internal fun ChannelsPage(permission: String?, onAnswer: (Boolean) -> Unit, next: () -> Unit) {
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onAnswer(granted)
        denied = !granted
    }
    NextWhen(permission == null, next)
    WizardPage(
        title = stringResource(R.string.wizard_channels_title),
        text = stringResource(if (permission == null) R.string.wizard_channels_done else R.string.wizard_channels_text),
        facts = listOf(stringResource(R.string.wizard_channels_fact)).takeIf { permission != null }.orEmpty(),
        note = stringResource(R.string.wizard_channels_denied).takeIf { denied && permission != null },
        problem = true,
        stage = denied to (permission == null),
    ) { focus ->
        if (permission == null) return@WizardPage DoneButton(next, focus)
        if (denied) {
            Button(onClick = next, modifier = focus) { Text(stringResource(R.string.wizard_next)) }
        } else {
            Button(onClick = { ask.launch(permission) }, modifier = focus) {
                Text(stringResource(R.string.wizard_continue))
            }
            OutlinedButton(onClick = next) { Text(stringResource(R.string.wizard_later)) }
        }
    }
}

/** Notification access, for the bell and "now playing": given in the system's settings, checked on return. */
@Composable
internal fun NoticesPage(missing: Boolean, onOpen: () -> Boolean, next: () -> Unit) {
    // Null until tried; then whether the device opened its screen.
    var opened by remember { mutableStateOf<Boolean?>(null) }
    NextWhen(!missing, next)
    val note = when {
        !missing -> null
        opened == false -> stringResource(R.string.wizard_notices_closed)
        opened == true -> stringResource(R.string.wizard_notices_not_yet)
        else -> null
    }
    WizardPage(
        title = stringResource(R.string.wizard_notices_title),
        text = stringResource(if (missing) R.string.wizard_notices_text else R.string.wizard_notices_done),
        note = note,
        problem = true,
        stage = opened to missing,
    ) { focus ->
        if (!missing) return@WizardPage DoneButton(next, focus)
        if (opened == false) {
            Button(onClick = next, modifier = focus) { Text(stringResource(R.string.wizard_next)) }
        } else {
            Button(onClick = { opened = onOpen() }, modifier = focus) {
                Text(stringResource(R.string.setup_open_settings))
            }
            OutlinedButton(onClick = next) { Text(stringResource(R.string.wizard_later)) }
        }
    }
}

private const val RUSSIAN = "ru"
private const val ENGLISH = "en"

/**
 * On a step just done, until the next one comes by itself: something for the focus to stand on.
 * With no button it went up to "Skip setup", and an impatient OK there left the setup.
 */
@Composable
internal fun DoneButton(next: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = next, modifier = modifier) { Text(stringResource(R.string.wizard_next)) }
}
