package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.access.NotificationAccess
import dev.lumbina.tv.core.domain.access.UsageAccess
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.diagnostics.DiagnosticsSnapshot
import dev.lumbina.tv.core.domain.diagnostics.LogArchive
import dev.lumbina.tv.core.domain.diagnostics.ReportExporter
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import dev.lumbina.tv.core.domain.diagnostics.snapshot
import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import dev.lumbina.tv.core.domain.health.SecureSettings
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.input.InputSettingsRepository
import dev.lumbina.tv.core.domain.speech.VoiceLanguageMode
import dev.lumbina.tv.core.domain.speech.VoiceLanguages
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [notifications], [usage]: whether the user allowed either special access. */
data class DiagnosticsState(
    val snapshot: DiagnosticsSnapshot,
    val log: List<String>,
    val notifications: Boolean = false,
    val usage: Boolean = false,
)

/** Where the last report went: a path, or null while none was saved; [failed] if saving failed. */
data class SavedReport(val path: String?, val failed: Boolean = false)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val system: SystemInfo,
    private val profile: DeviceProfile,
    private val health: AccessibilityHealth,
    private val secureSettings: SecureSettings,
    private val logArchive: LogArchive,
    private val exporter: ReportExporter,
    private val notificationAccess: NotificationAccess,
    private val usageAccess: UsageAccess,
    voiceLanguages: VoiceLanguages,
    inputSettings: InputSettingsRepository,
    installedApps: InstalledApps,
    layouts: HomeLayoutRepository,
) : ViewModel() {

    /** Service state and the log change from outside, with no callback: re-read now and then. */
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH_MILLIS)
        }
    }

    /** The log tail is the costly part (it reads the log files), so it is read less often. */
    private val logTail = flow {
        while (true) {
            emit(logArchive.tail(SCREEN_LOG_LINES))
            delay(LOG_REFRESH_MILLIS)
        }
    }.flowOn(Dispatchers.IO)

    private val snapshot = combine(
        voiceLanguages.state,
        inputSettings.settings,
        installedApps.observe(),
        layouts.layout,
        ticks,
    ) { voice, input, apps, layout, _ ->
        DiagnosticsSnapshot(
            system = system.snapshot(),
            deviceProfile = profile.id,
            stockLauncher = profile.stockLauncher?.flatten(),
            serviceRunning = health.isServiceBound(),
            canHealService = secureSettings.canWrite,
            remoteMic = profile.hasRemoteMic,
            voiceLanguages = voice.enabled.map { it.language },
            installedModels = voice.languages.filter { it.installed }.map { it.language },
            voiceMode = when (val mode = voice.mode) {
                VoiceLanguageMode.Auto -> "Auto"
                is VoiceLanguageMode.Only -> "Only ${mode.language}"
            },
            lumbinaKeyboard = input.useLumbinaKeyboard,
            installedApps = apps.size,
            favorites = layout.favorites.items.size,
            categories = layout.categories.size,
            hiddenApps = layout.hidden.size,
        )
    }.flowOn(Dispatchers.IO)

    /** Granted in the system's settings, with no callback: re-read with the rest. */
    private val grants = ticks.map { notificationAccess.granted() to usageAccess.granted() }.flowOn(Dispatchers.IO)

    val state: StateFlow<DiagnosticsState?> = combine(snapshot, logTail, grants) { snapshot, log, (notes, usage) ->
        DiagnosticsState(snapshot, log, notifications = notes, usage = usage)
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun openNotificationAccess() {
        notificationAccess.openSettings()
    }

    fun openUsageAccess() {
        usageAccess.openSettings()
    }

    private val mutableSaved = MutableStateFlow(SavedReport(null))
    val saved: StateFlow<SavedReport> = mutableSaved.asStateFlow()

    /** The page was left: where the last report went is not news any more. */
    fun forgetSaved() {
        mutableSaved.value = SavedReport(null)
    }

    fun saveReport() {
        val snapshot = state.value?.snapshot ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val now = Date()
            val text = snapshot.toReport(
                SimpleDateFormat(TIME, Locale.US).format(now),
                logArchive.tail(REPORT_LOG_LINES),
            )
            val path = exporter.save("lumbina-report-${SimpleDateFormat(FILE_TIME, Locale.US).format(now)}.txt", text)
            mutableSaved.value = SavedReport(path, failed = path == null)
        }
    }

    private companion object {
        const val REFRESH_MILLIS = 3_000L
        const val LOG_REFRESH_MILLIS = 15_000L
        const val SCREEN_LOG_LINES = 40
        const val REPORT_LOG_LINES = 500
        const val TIME = "yyyy-MM-dd HH:mm:ss"
        const val FILE_TIME = "yyyyMMdd-HHmmss"
    }
}
