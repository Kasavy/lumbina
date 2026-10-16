package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.domain.remote.OtherHome
import dev.lumbina.tv.core.domain.remote.OtherHomeChoice
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [openOnPowerOn]: Lumbina comes up when the device is turned on; the service does it, with or without debugging. */
data class HomeKeyState(val otherHome: OtherHomeChoice, val openOnPowerOn: Boolean)

@HiltViewModel
class HomeKeySettingsViewModel @Inject constructor(
    private val otherHome: OtherHome,
    private val access: AccessSettingsRepository,
) : ViewModel() {

    val state: StateFlow<HomeKeyState?> = combine(otherHome.choice, access.settings) { home, access ->
        HomeKeyState(home, access.openOnPowerOn)
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun setOpenOnPowerOn(open: Boolean) {
        viewModelScope.launch { access.update { it.copy(openOnPowerOn = open) } }
    }

    fun useHome(screen: HomeScreen) {
        viewModelScope.launch { otherHome.use(screen) }
    }
}
