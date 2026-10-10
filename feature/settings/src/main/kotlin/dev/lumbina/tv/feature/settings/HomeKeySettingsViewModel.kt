package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.domain.remote.OtherHome
import dev.lumbina.tv.core.domain.remote.OtherHomeChoice
import dev.lumbina.tv.core.domain.remote.RemoteSettingsRepository
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeKeyState(val doubleHomeRecents: Boolean, val otherHome: OtherHomeChoice)

@HiltViewModel
class HomeKeySettingsViewModel @Inject constructor(
    private val settings: RemoteSettingsRepository,
    private val otherHome: OtherHome,
) : ViewModel() {

    val state: StateFlow<HomeKeyState?> = combine(settings.settings, otherHome.choice) { remote, home ->
        HomeKeyState(remote.doubleHomeRecents, home)
    }.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun toggleDoubleHome() {
        viewModelScope.launch { settings.update { it.copy(doubleHomeRecents = !it.doubleHomeRecents) } }
    }

    fun useHome(screen: HomeScreen) {
        viewModelScope.launch { otherHome.use(screen) }
    }
}
