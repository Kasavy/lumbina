// Panels take a page's view model only as its command interface (e.g. HomeSettingsCommands);
// one small function per settings page.
@file:Suppress("ViewModelForwarding", "TooManyFunctions")

package dev.lumbina.tv.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.ServiceConsentDialog
import dev.lumbina.tv.core.ui.rememberServiceConsent

/**
 * One settings page with its view model, created when the page is first shown, so pages that
 * aren't visited do no work. The app makes the view models live as long as the activity (a page
 * opened again shows what it last showed from its first frame), so what a page said once, about
 * something just done, is forgotten as the page is left.
 */
@Composable
internal fun SettingsPageContent(page: SettingsPage, onPreviewScreensaver: () -> Unit, openAccess: (() -> Unit)?) {
    when (page) {
        SettingsPage.Rows -> RowsPage()

        SettingsPage.Channels -> ChannelsPage()

        SettingsPage.Widgets -> WidgetsPage()

        SettingsPage.Wallpaper -> WallpaperPage()

        SettingsPage.Media -> Unit

        // opened as a screen of its own, never in the panel
        SettingsPage.Look -> LookPage()

        SettingsPage.Voice -> VoicePage()

        SettingsPage.HomeKey -> ButtonsAndKeyboard()

        SettingsPage.Phone -> PhonePage()

        SettingsPage.Sleep -> SleepPage(onPreviewScreensaver, openAccess)

        SettingsPage.State -> StatePage(openAccess)

        SettingsPage.About -> AboutPage()

        SettingsPage.Access -> AccessPage()
    }
}

@Composable
private fun RowsPage(viewModel: HomeSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomePanel(state = state, commands = viewModel, onSpotlight = viewModel::spotlight)
}

@Composable
private fun ChannelsPage(viewModel: ChannelSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Also back from the system's settings, where the permission can be granted as well.
    LifecycleResumeEffect(viewModel) {
        viewModel.recheck()
        onPauseOrDispose {}
    }
    ChannelsPanel(state = state, onToggleChannel = viewModel::toggle, onPermission = { viewModel.recheck() })
}

@Composable
private fun WidgetsPage(viewModel: WidgetsSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val citySearch by viewModel.citySearch.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose(viewModel::closeSearch) }
    WidgetsPanel(state = state, citySearch = citySearch, commands = viewModel)
}

@Composable
private fun WallpaperPage(viewModel: WallpaperSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WallpaperPanel(state = state, commands = viewModel)
}

@Composable
private fun LookPage(viewModel: HomeSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Choosing a language re-creates the screen, so reading it once per composition is enough.
    val language = remember { viewModel.language }
    LookPanel(state = state, commands = viewModel, language = language, languageSupported = viewModel.languageSupported)
}

@Composable
private fun VoicePage(viewModel: VoiceSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val recognizer by viewModel.recognizer.collectAsStateWithLifecycle()
    // All three at once: a page that came in parts would shift as each arrived.
    val who = recognizer
    val carriesOut = commands
    VoicePanel(
        state = state.takeIf { who != null && carriesOut != null },
        supported = viewModel.voiceSupported,
        onMode = viewModel::setMode,
        onLanguageClick = viewModel::onLanguageClick,
        onRemove = viewModel::remove,
        commands = carriesOut ?: true,
        onToggleCommands = viewModel::toggleCommands,
        recognizer = who ?: Recognizer(),
        onChooseProvider = viewModel::chooseProvider,
    )
}

/** The page itself, or the remote's buttons opened from its first row. */
@Composable
private fun ButtonsAndKeyboard() {
    var buttons by rememberSaveable { mutableStateOf(false) }
    var cameBack by remember { mutableStateOf(false) }
    val hold = LocalParkFocus.current
    if (buttons) {
        ButtonsPages(
            onLeave = {
                buttons = false
                cameBack = true
            },
        )
    } else {
        SettingsColumn {
            // Here, not with the rows that wait for their settings: it is there at once, and the page's
            // first row is where the focus goes.
            SettingRow(
                onClick = {
                    hold()
                    buttons = true
                },
                headlineContent = { Text(stringResource(R.string.buttons_title)) },
                modifier = Modifier.focusedOnReturn(cameBack) { cameBack = false },
            )
            HomeKeyAndKeyboard()
        }
    }
}

/**
 * The rows about Home's menu and power-on, then the keyboard's: two view models, shown together
 * once both have read their settings. Each on its own would push the other down as it came.
 */
@Composable
private fun HomeKeyAndKeyboard(
    keys: HomeKeySettingsViewModel = hiltViewModel(),
    keyboard: InputSettingsViewModel = hiltViewModel(),
) {
    val keysState by keys.state.collectAsStateWithLifecycle()
    val keyboardState by keyboard.state.collectAsStateWithLifecycle()
    val ready = keysState != null && keyboardState != null
    HomeKeyPanel(
        state = keysState.takeIf { ready },
        onUseHome = keys::useHome,
        onOpenOnPowerOn = keys::setOpenOnPowerOn,
    )
    InputPanel(state = keyboardState.takeIf { ready }, onToggleLumbinaKeyboard = keyboard::toggleLumbinaKeyboard)
}

@Composable
private fun PhonePage(viewModel: PhoneRemoteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PhoneRemotePanel(
        state = state,
        onToggle = viewModel::toggle,
        onNewKey = viewModel::newKey,
        onToggleScreensaver = viewModel::toggleOnScreensaver,
    )
}

@Composable
private fun SleepPage(
    onPreviewScreensaver: () -> Unit,
    openAccess: (() -> Unit)?,
    viewModel: SleepSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose(viewModel::forgetRefusal) }
    SleepPanel(
        state = state,
        commands = viewModel,
        onPreviewScreensaver = onPreviewScreensaver,
        openAccess = openAccess,
        computerCommands = viewModel.computerCommands,
    )
}

@Composable
private fun StatePage(
    openAccess: (() -> Unit)?,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
    phone: PhoneRemoteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val phoneState by phone.state.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose(viewModel::forgetDone) }
    DiagnosticsPanel(
        state = state,
        saved = saved,
        phone = phoneState,
        parts = parts,
        onTurnOn = viewModel::turnOn,
        openAccess = openAccess,
        onSaveReport = viewModel::saveReport,
        onNotificationAccess = viewModel::openNotificationAccess,
        onUsageAccess = viewModel::openUsageAccess,
    )
}

@Composable
private fun AccessPage(viewModel: AccessViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // "Connect" and keeping the service on turn the remote's service on: first say what it does.
    val consent = rememberServiceConsent()
    val commands = remember(viewModel) {
        object : AccessCommands by viewModel {
            override fun connect() = consent.ask(viewModel::connect)

            override fun toggleKeepService() {
                val turningOn = viewModel.state.value?.keepsService == false
                if (turningOn) consent.ask(viewModel::toggleKeepService) else viewModel.toggleKeepService()
            }
        }
    }
    AccessPanel(state = state, commands = commands)
    ServiceConsentDialog(consent, viewModel.serviceScope)
}

@Composable
private fun AboutPage(viewModel: AboutViewModel = hiltViewModel()) {
    val setupHidden by viewModel.setupHidden.collectAsStateWithLifecycle()
    var offered by remember { mutableStateOf(false) }
    LaunchedEffect(setupHidden) { if (setupHidden) offered = true }
    val update by viewModel.update.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose(viewModel::forgetDone) }
    AboutPanel(
        system = viewModel.system,
        deviceProfile = viewModel.deviceProfile,
        update = update,
        onUpdate = viewModel::onUpdate,
        setup = SetupOffer(setupHidden, viewModel::showSetup).takeIf { offered || setupHidden },
    )
}
