package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.common.OUR_SCREENSAVER
import dev.lumbina.tv.core.domain.backdrop.ScreensaverSetup
import dev.lumbina.tv.core.domain.backdrop.screensaverCommands
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SleepPanelState(val sleep: SleepSettings, val screensaverIsLumbina: Boolean, val screensaverDenied: Boolean)

/** What the sleep & screensaver panel can do. */
interface SleepSettingsCommands {
    fun setAutoSleep(minutes: Int?)
    fun setScreensaverDelay(minutes: Int?)
    fun selectScreensaver()
    fun toggleOnlinePhotos()
}

@HiltViewModel
class SleepSettingsViewModel @Inject constructor(
    private val sleep: SleepSettingsRepository,
    private val screensaver: ScreensaverSetup,
    @Named(OUR_SCREENSAVER) screensaverComponent: String,
) : ViewModel(),
    SleepSettingsCommands {

    /** For a computer, where Lumbina may not make itself the system screensaver. */
    val computerCommands: List<String> = screensaverCommands(screensaverComponent)

    /** Whether Lumbina is the system screensaver, and whether the last attempt to make it so was refused. */
    private val screensaverStatus = MutableStateFlow(false to false)

    val state: StateFlow<SleepPanelState?> =
        combine(sleep.settings, screensaverStatus) { sleep, (isLumbina, denied) ->
            SleepPanelState(sleep, isLumbina, denied)
        }.onStart { readScreensaver() } // again on coming back, e.g. from the system settings
            .stateIn(viewModelScope, WhileUiSubscribed, null)

    override fun setAutoSleep(minutes: Int?) {
        viewModelScope.launch { sleep.update { it.copy(autoSleepMinutes = minutes) } }
    }

    override fun setScreensaverDelay(minutes: Int?) {
        viewModelScope.launch { sleep.update { it.copy(screensaverMinutes = minutes) } }
    }

    override fun selectScreensaver() {
        viewModelScope.launch {
            screensaverStatus.value = withContext(Dispatchers.IO) {
                val selected = screensaver.select()
                screensaver.isLumbina to !selected
            }
        }
    }

    override fun toggleOnlinePhotos() {
        viewModelScope.launch { sleep.update { it.copy(onlinePhotos = !it.onlinePhotos) } }
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
