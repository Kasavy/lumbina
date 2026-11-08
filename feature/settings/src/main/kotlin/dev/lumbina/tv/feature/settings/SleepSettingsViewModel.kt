package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.common.OUR_SCREENSAVER
import dev.lumbina.tv.core.domain.backdrop.MediaLibrary
import dev.lumbina.tv.core.domain.backdrop.MediaMarksRepository
import dev.lumbina.tv.core.domain.backdrop.ScreensaverPicker
import dev.lumbina.tv.core.domain.backdrop.ScreensaverSetup
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.backdrop.screensaverCommands
import dev.lumbina.tv.core.domain.sleep.ScreensaverPhotos
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [marked]: how many of the user's [files] have the screensaver mark; [wallpaper], what the
 * wallpaper shows, for the screensaver to show the same.
 */
data class SleepPanelState(
    val sleep: SleepSettings,
    val screensaverIsLumbina: Boolean,
    val screensaverDenied: Boolean,
    /** Where the user can choose Lumbina's screensaver by hand, with no access needed; null: nowhere. */
    val screensaverPicker: ScreensaverPicker? = null,
    val marked: Int = 0,
    val files: Int = 0,
    val plugins: List<WallpaperPluginInfo> = emptyList(),
    val wallpaper: WallpaperSource = WallpaperSource.Gradient,
)

/** What the screensaver & sleep panel can do. */
interface SleepSettingsCommands {
    fun setAutoSleep(minutes: Int?)
    fun setScreensaverDelay(minutes: Int?)
    fun setScreensaverChange(minutes: Int)
    fun selectScreensaver()
    fun openScreensaverPicker()
    fun setScreensaverPhotos(photos: ScreensaverPhotos)
    fun openPluginSettings(id: String)
}

@HiltViewModel
class SleepSettingsViewModel @Inject constructor(
    private val sleep: SleepSettingsRepository,
    private val screensaver: ScreensaverSetup,
    private val library: MediaLibrary,
    marks: MediaMarksRepository,
    private val plugins: WallpaperPlugins,
    wallpaper: WallpaperSettingsRepository,
    @Named(OUR_SCREENSAVER) screensaverComponent: String,
) : ViewModel(),
    SleepSettingsCommands {

    /** For a computer, where Lumbina may not make itself the system screensaver. */
    val computerCommands: List<String> = screensaverCommands(screensaverComponent)

    /** Whether Lumbina is the system screensaver, and whether the last attempt to make it so was refused. */
    private val screensaverStatus = MutableStateFlow(false to false)

    val state: StateFlow<SleepPanelState?> =
        combine(
            sleep.settings,
            screensaverStatus,
            marks.settings,
            wallpaper.settings.map { it.source }.distinctUntilChanged(),
        ) { sleep, (isLumbina, denied), marked, source ->
            val files = library.files()
            // Chosen meanwhile by hand (the system's list): the ways to it have done their work.
            val stillDenied = denied && !isLumbina
            SleepPanelState(
                sleep = sleep,
                screensaverIsLumbina = isLumbina,
                screensaverDenied = stillDenied,
                screensaverPicker = if (stillDenied) screensaver.picker else null,
                marked = files.count { marked.onScreensaver(it.id) },
                files = files.size,
                plugins = plugins.installed(),
                wallpaper = source,
            )
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WhileUiSubscribed, null)

    override fun setAutoSleep(minutes: Int?) {
        viewModelScope.launch { sleep.update { it.copy(autoSleepMinutes = minutes) } }
    }

    override fun setScreensaverDelay(minutes: Int?) {
        viewModelScope.launch { sleep.update { it.copy(screensaverMinutes = minutes) } }
    }

    override fun setScreensaverChange(minutes: Int) {
        viewModelScope.launch { sleep.update { it.copy(screensaverChangeMinutes = minutes) } }
    }

    override fun openPluginSettings(id: String) {
        plugins.openSettings(id)
    }

    /**
     * Asked for and refused: tried again when the page comes back, as the user may have gone to get
     * the permission ("Access", a computer). Kept while the settings are open.
     */
    @Volatile private var wanted = false

    /** One choosing at a time: the user's press and the quiet retry on coming back would race for [wanted]. */
    private val choosing = Mutex()

    /** Without the permission, straight to the device's own screensaver screen where it has one. */
    override fun selectScreensaver() {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = choosing.withLock {
                screensaver.select().also { selected ->
                    wanted = !selected
                    screensaverStatus.value = screensaver.isLumbina to !selected
                }
            }
            val picker = if (selected) null else screensaver.picker
            if (picker == ScreensaverPicker.Screen) screensaver.openPicker()
        }
    }

    override fun openScreensaverPicker() {
        viewModelScope.launch(Dispatchers.IO) { screensaver.openPicker() }
    }

    override fun setScreensaverPhotos(photos: ScreensaverPhotos) {
        viewModelScope.launch { sleep.update { it.copy(screensaverPhotos = photos) } }
    }

    /** The page was left: the refusal was about that attempt. */
    fun forgetRefusal() {
        screensaverStatus.value = screensaverStatus.value.first to false
    }

    /** Reads which screensaver the system has, at each showing: it may have been chosen in the system's settings. */
    fun recheck() = readScreensaver()

    private fun readScreensaver() {
        viewModelScope.launch {
            val isLumbina = withContext(Dispatchers.IO) {
                choosing.withLock {
                    // Quietly: no list opens by itself here, the user didn't press anything.
                    if (wanted && !screensaver.isLumbina && screensaver.select()) wanted = false
                    // Done another way (the system's list): nothing left to try, nor to undo later.
                    screensaver.isLumbina.also { if (it) wanted = false }
                }
            }
            screensaverStatus.value = isLumbina to screensaverStatus.value.second
        }
    }
}
