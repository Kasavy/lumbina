package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.phone.LocalAddress
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettingsRepository
import dev.lumbina.tv.core.domain.phone.phoneRemoteAddress
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [address] is null while the device has no local network address. */
data class PhoneRemoteState(val enabled: Boolean, val address: String?)

@HiltViewModel
class PhoneRemoteViewModel @Inject constructor(
    private val settings: PhoneRemoteSettingsRepository,
    private val localAddress: LocalAddress,
) : ViewModel() {

    val state: StateFlow<PhoneRemoteState?> = settings.settings
        .map { PhoneRemoteState(it.enabled, phoneRemoteAddress(localAddress.ipv4(), it.key)) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, WhileUiSubscribed, null)

    fun toggle() {
        viewModelScope.launch { settings.update { it.copy(enabled = !it.enabled) } }
    }

    /** A new key: phones that opened the old address can no longer control the device. */
    fun newKey() {
        viewModelScope.launch { settings.update { it.copy(key = PhoneRemoteSettings.newKey()) } }
    }
}
