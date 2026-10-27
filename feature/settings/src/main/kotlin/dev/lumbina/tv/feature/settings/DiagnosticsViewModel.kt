package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.access.NotificationAccess
import dev.lumbina.tv.core.domain.access.UsageAccess
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.channels.ChannelCensus
import dev.lumbina.tv.core.domain.channels.TvChannels
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import dev.lumbina.tv.core.domain.device.DeviceAnswer
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.device.FirmwarePart
import dev.lumbina.tv.core.domain.device.FirmwareParts
import dev.lumbina.tv.core.domain.device.PackageStates
import dev.lumbina.tv.core.domain.diagnostics.AccessFacts
import dev.lumbina.tv.core.domain.diagnostics.AskDevice
import dev.lumbina.tv.core.domain.diagnostics.ChannelsFacts
import dev.lumbina.tv.core.domain.diagnostics.DiagnosticsSnapshot
import dev.lumbina.tv.core.domain.diagnostics.FirmwarePackage
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [notifications], [usage]: whether the user allowed either special access. */
data class DiagnosticsState(
    val snapshot: DiagnosticsSnapshot,
    val log: List<String>,
    val notifications: Boolean = false,
    val usage: Boolean = false,
)

/**
 * Where the last report went: a path, or null while none was saved; [failed] if saving failed.
 * [saving]: one is being put together now, which takes a while where the device is asked questions.
 */
data class SavedReport(val path: String?, val failed: Boolean = false, val saving: Boolean = false)

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
    private val localAccess: LocalAccess,
    private val askDevice: AskDevice,
    private val firmware: FirmwareParts,
    private val packages: PackageStates,
    private val census: ChannelCensus,
    private val channels: TvChannels,
    private val listings: TvListingsPermission,
    accessSettings: AccessSettingsRepository,
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

    /** Granted in the system's settings, with no callback: re-read with the rest. */
    private val grants = ticks.map { notificationAccess.granted() to usageAccess.granted() }.flowOn(Dispatchers.IO)

    /** Reading the adb state opens a connection to it, so it is polled only as often as the log. */
    private val debugging = flow {
        while (true) {
            emit(if (profile.localAdb) localAccess.state().name else null)
            delay(LOG_REFRESH_MILLIS)
        }
    }.flowOn(Dispatchers.IO)

    private val access = combine(accessSettings.settings, debugging, grants) { settings, debugging, (notes, usage) ->
        AccessFacts(debugging, settings.openOnPowerOn, notifications = notes, usage = usage)
    }

    private val channelFacts = combine(channels.channels(), channels.watchNext(), ticks) { feeds, next, _ ->
        ChannelsFacts(
            provider = channels.provider(),
            permissionMissing = listings.missing() != null,
            channels = feeds.size,
            programs = feeds.sumOf { it.programs.size },
            watchNext = next.size,
        )
    }.flowOn(Dispatchers.IO)

    private val snapshot = combine(
        voiceLanguages.state,
        inputSettings.settings,
        installedApps.observe(),
        layouts.layout,
        combine(access, channelFacts, ::Pair),
    ) { voice, input, apps, layout, (allowed, tv) ->
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
            access = allowed,
            channels = tv,
            firmware = (
                listOfNotNull(profile.stockLauncher?.packageName) + profile.firmwareParts.map {
                    it.packageName
                }
                )
                .map { FirmwarePackage(it, system.packageVersion(it), packages.of(it)) },
            partsOff = firmware.off(),
            inputs = profile.inputs?.list().orEmpty().map { "${it.name} (${it.id})" },
        )
    }.flowOn(Dispatchers.IO)

    val state: StateFlow<DiagnosticsState?> = combine(snapshot, logTail) { snapshot, log ->
        DiagnosticsState(snapshot, log, notifications = snapshot.access.notifications, usage = snapshot.access.usage)
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun openNotificationAccess() {
        notificationAccess.openSettings()
    }

    fun openUsageAccess() {
        usageAccess.openSettings()
    }

    private val mutableParts = MutableStateFlow(PartsState(firmware.canTurnOn))
    val parts: StateFlow<PartsState> = mutableParts.asStateFlow()

    /** One at a time: a press while a part is being turned on would run the command again. */
    fun turnOn(part: FirmwarePart) {
        if (mutableParts.value.working != null) return
        mutableParts.update { it.started(part) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = firmware.turnOn(part)
            mutableParts.update { it.finished(part, result) }
        }
    }

    private val mutableSaved = MutableStateFlow(SavedReport(null))
    val saved: StateFlow<SavedReport> = mutableSaved.asStateFlow()

    /** The page was left: where the last report went and what was turned on or failed is not news any more. */
    fun forgetDone() {
        mutableSaved.value = SavedReport(null)
        // What is being turned on still is: forgotten, it could be started a second time.
        mutableParts.update { PartsState(firmware.canTurnOn, working = it.working) }
    }

    /** One report at a time: a second press while one is being put together would ask the device twice at once. */
    fun saveReport() {
        val snapshot = state.value?.snapshot ?: return
        if (mutableSaved.value.saving) return
        mutableSaved.value = SavedReport(null, saving = true)
        viewModelScope.launch(Dispatchers.IO) {
            val now = Date()
            // Asked before the log is read: what the questions stirred up belongs in this report.
            val answered = askDevice() + DeviceAnswer("TV provider, app by app", census.take().joinToString("\n"))
            val text = snapshot.toReport(
                SimpleDateFormat(TIME, Locale.US).format(now),
                logArchive.tail(REPORT_LOG_LINES),
                answered,
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
