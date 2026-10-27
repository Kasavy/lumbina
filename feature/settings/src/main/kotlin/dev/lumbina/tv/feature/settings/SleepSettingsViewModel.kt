package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.common.OUR_SCREENSAVER
import dev.lumbina.tv.core.domain.backdrop.MediaLibrary
import dev.lumbina.tv.core.domain.backdrop.MediaMarksRepository
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [marked]: how many of the user's [files] have the screensaver mark; [wallpaper], what the
 * wallpaper shows, for the screensaver to show the same.
 */
data class SleepPanelState(
    val sleep: SleepSettings,
    val screensaverIsLumbina: Boolean,
    val screensaverDenied: Boolean,
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
            SleepPanelState(
                sleep = sleep,
                screensaverIsLumbina = isLumbina,
                screensaverDenied = denied,
                marked = files.count { marked.onScreensaver(it.id) },
                files = files.size,
                plugins = plugins.installed(),
                wallpaper = source,
            )
        }.flowOn(Dispatchers.IO).onStart { readScreensaver() } // again on coming back, e.g. from the system settings
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

    override fun selectScreensaver() {
        viewModelScope.launch {
            screensaverStatus.value = withContext(Dispatchers.IO) {
                val selected = screensaver.select()
                screensaver.isLumbina to !selected
            }
        }
    }

    override fun setScreensaverPhotos(photos: ScreensaverPhotos) {
        viewModelScope.launch { sleep.update { it.copy(screensaverPhotos = photos) } }
    }

    /** The page was left: the refusal was about that attempt. */
    fun forgetRefusal() {
        screensaverStatus.value = screensaverStatus.value.first to false
    }

    private fun readScreensaver() {
        viewModelScope.launch {
            val isLumbina = withContext(Dispatchers.IO) { screensaver.isLumbina }
            screensaverStatus.value = isLumbina to screensaverStatus.value.second
        }
    }
}
