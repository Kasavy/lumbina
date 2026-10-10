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
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import dev.lumbina.tv.core.domain.diagnostics.snapshot
import dev.lumbina.tv.core.ui.ServiceConsentDialog
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import dev.lumbina.tv.core.ui.rememberServiceConsent
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        SettingsPage.Look -> LookPage()
        SettingsPage.Voice -> VoicePage()
        SettingsPage.HomeKey -> HomeKeyPage()
        SettingsPage.Keyboard -> KeyboardPage()
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
    HomePanel(state = state, commands = viewModel)
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
    VoicePanel(
        state = state,
        supported = viewModel.voiceSupported,
        onMode = viewModel::setMode,
        onLanguageClick = viewModel::onLanguageClick,
        onRemove = viewModel::remove,
        commands = commands,
        onToggleCommands = viewModel::toggleCommands,
        recognizer = recognizer,
        onChooseProvider = viewModel::chooseProvider,
    )
}

@Composable
private fun HomeKeyPage(viewModel: HomeKeySettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeKeyPanel(state = state, onToggleDoubleHome = viewModel::toggleDoubleHome, onUseHome = viewModel::useHome)
}

@Composable
private fun KeyboardPage(viewModel: InputSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InputPanel(state = state, onToggleLumbinaKeyboard = viewModel::toggleLumbinaKeyboard)
}

@Composable
private fun PhonePage(viewModel: PhoneRemoteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PhoneRemotePanel(state = state, onToggle = viewModel::toggle, onNewKey = viewModel::newKey)
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
private fun StatePage(openAccess: (() -> Unit)?, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val parts by viewModel.parts.collectAsStateWithLifecycle()
    DisposableEffect(viewModel) { onDispose(viewModel::forgetDone) }
    DiagnosticsPanel(
        state = state,
        saved = saved,
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
    AboutPanel(
        system = viewModel.system,
        deviceProfile = viewModel.deviceProfile,
        setup = SetupOffer(setupHidden, viewModel::showSetup).takeIf { offered || setupHidden },
    )
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    profile: DeviceProfile,
    system: SystemInfo,
    private val access: AccessSettingsRepository,
) : ViewModel() {
    val system = system.snapshot()
    val deviceProfile: String = profile.id

    /** Whether the user hid the first-setup card of the home screen: it can be asked back here. */
    val setupHidden: StateFlow<Boolean> =
        access.settings.map { it.setupCardHidden }.stateIn(viewModelScope, WhileUiSubscribed, false)

    fun showSetup() {
        viewModelScope.launch { access.update { it.copy(setupCardHidden = false) } }
    }
}
