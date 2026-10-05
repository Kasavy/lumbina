package dev.lumbina.tv.feature.onboarding

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.speech.ModelState
import dev.lumbina.tv.core.ui.ServiceConsentDialog
import dev.lumbina.tv.core.ui.rememberServiceConsent
import java.util.Locale

@Composable
fun SetupRoute(modifier: Modifier = Modifier, active: Boolean = true, viewModel: SetupViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var computer by remember { mutableStateOf(false) }
    // "Hide" and a successful "Connect" take the card away from under the focus, which then
    // lands on the top bar; it goes down to the rows instead. Not while another screen is shown
    // over home ([active] false): the focus there is not the card's to move.
    var refocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val current = status?.takeUnless { it.complete }
    // A failed "Connect" leaves the card in place: nothing to refocus after all.
    LaunchedEffect(current?.connect) {
        if (current?.connect.let { it != null && it != ConnectResult.Connecting && it != ConnectResult.TurningOn }) {
            refocus = false
        }
    }
    if (current == null) {
        if (refocus) {
            LaunchedEffect(Unit) {
                withFrameNanos {}
                if (active) focusManager.moveFocus(FocusDirection.Down)
                refocus = false
            }
        }
        return
    }
    // Every way to turn the service on first says what it does.
    val consent = rememberServiceConsent()
    val actions = SetupActions(
        onHide = {
            refocus = true
            viewModel.hide()
        },
        onDeveloperSettings = viewModel::openDeveloperSettings,
        onConnect = {
            consent.ask {
                refocus = true
                viewModel.connect()
            }
        },
        onAccessibilitySettings = { consent.ask(viewModel::openAccessibilitySettings) },
        onComputer = { consent.ask { computer = true } },
        onDownloadSpeechModels = viewModel::downloadSpeechModels,
    )
    SetupCard(current, actions, modifier)
    ServiceConsentDialog(consent, viewModel.serviceScope)
    val packageName = LocalContext.current.packageName
    val commands by produceState<ComputerCommands?>(null, computer) {
        value = if (computer) viewModel.computerCommands(packageName) else null
    }
    commands?.let { ComputerDialog(it, onDismiss = { computer = false }) }
}

/** What the card's buttons do. */
internal class SetupActions(
    val onHide: () -> Unit,
    val onDeveloperSettings: () -> Unit,
    val onConnect: () -> Unit,
    val onAccessibilitySettings: () -> Unit,
    val onComputer: () -> Unit,
    val onDownloadSpeechModels: () -> Unit,
)

/**
 * Shown on the home screen until the remote's service runs and voice input can work: numbered
 * steps, each with its buttons. "Hide" hides it for good (the steps stay in the settings).
 */
@Composable
internal fun SetupCard(status: SetupStatus, actions: SetupActions, modifier: Modifier = Modifier) {
    val onlyStarting = status.starting && status.voice?.ready != false
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        if (onlyStarting) {
            Text(stringResource(R.string.setup_starting), style = MaterialTheme.typography.titleMedium)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (status.userSteps == 1) R.string.setup_one_step else R.string.setup_two_steps),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = actions.onHide) { Text(stringResource(R.string.setup_hide)) }
        }
        var number = 1
        when {
            status.serviceRunning -> Unit

            status.starting -> Text(
                stringResource(R.string.setup_starting),
                style = MaterialTheme.typography.bodyMedium,
            )

            else -> ServiceStep(number++, status, actions)
        }
        status.voice?.takeUnless { it.ready }?.let { VoiceStep(number, it, actions.onDownloadSpeechModels) }
    }
}

@Composable
private fun ServiceStep(number: Int, status: SetupStatus, actions: SetupActions, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.setup_home_title)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        when (status.path) {
            ServicePath.LocalAdb -> {
                Step(number, title, adbHint(status)) {
                    StepButton(
                        stringResource(R.string.setup_dev_settings),
                        actions.onDeveloperSettings,
                        !status.debuggingOn,
                    )
                    StepButton(stringResource(R.string.setup_connect), actions.onConnect, status.debuggingOn)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = STEP_INDENT)) {
                    Text(stringResource(R.string.setup_trouble), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = actions.onComputer) { Text(stringResource(R.string.setup_from_computer)) }
                }
            }

            ServicePath.AccessibilitySettings -> Step(number, title, stringResource(R.string.setup_home_settings)) {
                StepButton(stringResource(R.string.setup_open_settings), actions.onAccessibilitySettings, true)
            }

            ServicePath.Computer -> Step(number, title, stringResource(R.string.setup_home_computer)) {
                StepButton(stringResource(R.string.setup_how), actions.onComputer, true)
            }
        }
    }
}

@Composable
private fun adbHint(status: SetupStatus): String = stringResource(
    when (status.connect) {
        ConnectResult.Connecting -> R.string.setup_home_connecting
        ConnectResult.NotAllowed -> R.string.setup_home_not_allowed
        ConnectResult.DebuggingOff -> R.string.setup_home_debugging_off
        ConnectResult.NotKept -> R.string.setup_home_not_kept
        ConnectResult.TurningOn -> R.string.setup_home_turning_on
        ConnectResult.Failed -> R.string.setup_home_failed
        null -> if (status.debuggingOn) R.string.setup_home_adb_on else R.string.setup_home_adb
    },
)

@Composable
private fun VoiceStep(number: Int, voice: VoiceSetup, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val names = voice.models.keys.map { languageName(it) }.joinToString()
    val size = Formatter.formatShortFileSize(context, voice.missingBytes)
    val failed = voice.models.values.any { it is ModelState.Failed }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        Step(number, stringResource(R.string.setup_voice_title), stringResource(R.string.setup_voice, names, size)) {
            if (!voice.busy) {
                val label = if (failed) R.string.setup_voice_retry else R.string.setup_voice_download
                StepButton(stringResource(label), onDownload, true)
            }
        }
        if (voice.busy || failed) {
            voice.models.forEach { (language, state) ->
                Text(
                    text = modelLine(language, state),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = STEP_INDENT),
                )
            }
        }
    }
}

/** A numbered step: the number, a title with a hint below, and its buttons on the right. */
@Composable
private fun Step(
    number: Int,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    buttons: @Composable () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(NUMBER_SIZE).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        ) {
            Text(number.toString(), style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    }
}

/** The step's main button is filled; the others are outlined. */
@Composable
private fun StepButton(label: String, onClick: () -> Unit, main: Boolean, modifier: Modifier = Modifier) {
    if (main) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun modelLine(language: String, state: ModelState): String {
    val name = languageName(language)
    return when (state) {
        ModelState.Missing -> stringResource(R.string.model_missing, name)

        is ModelState.Downloading -> stringResource(
            R.string.model_downloading,
            name,
            (state.fraction * PERCENT).toInt(),
        )

        ModelState.Unpacking -> stringResource(R.string.model_unpacking, name)

        ModelState.Installed -> stringResource(R.string.model_installed, name)

        is ModelState.Failed -> stringResource(
            when (state.reason) {
                ModelState.Reason.Network -> R.string.model_failed_network
                ModelState.Reason.Corrupt -> R.string.model_failed_corrupt
                ModelState.Reason.NoSpace -> R.string.model_failed_space
                ModelState.Reason.Storage -> R.string.model_failed_storage
            },
            name,
        )
    }
}

/** The language of [tag], named in the UI's language. */
@Composable
private fun languageName(tag: String): String =
    Locale.forLanguageTag(tag).getDisplayLanguage(LocalConfiguration.current.locales[0])

private const val PERCENT = 100
private val NUMBER_SIZE = 24.dp
private val STEP_INDENT = 40.dp
