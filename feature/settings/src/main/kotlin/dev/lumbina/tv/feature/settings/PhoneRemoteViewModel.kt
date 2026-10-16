package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.phone.LocalAddress
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettingsRepository
import dev.lumbina.tv.core.domain.phone.phoneRemoteAddress
import dev.lumbina.tv.core.domain.phone.phoneReportAddress
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [address] is null while the device has no local network address. */
data class PhoneRemoteState(
    val enabled: Boolean,
    val address: String?,
    val onScreensaver: Boolean = false,
    /** Where a phone gets the last diagnostics report; null while the page is off or there is no network. */
    val reportAddress: String? = null,
)

@HiltViewModel
class PhoneRemoteViewModel @Inject constructor(
    private val settings: PhoneRemoteSettingsRepository,
    private val localAddress: LocalAddress,
) : ViewModel() {

    val state: StateFlow<PhoneRemoteState?> = settings.settings
        .map {
            val ip = localAddress.ipv4()
            val report = phoneReportAddress(ip, it.key).takeIf { _ -> it.enabled }
            PhoneRemoteState(it.enabled, phoneRemoteAddress(ip, it.key), it.onScreensaver, report)
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, WhileUiSubscribed, null)

    fun toggle() {
        viewModelScope.launch { settings.update { it.copy(enabled = !it.enabled) } }
    }

    fun toggleOnScreensaver() {
        viewModelScope.launch { settings.update { it.copy(onScreensaver = !it.onScreensaver) } }
    }

    /** A new key: phones that opened the old address can no longer control the device. */
    fun newKey() {
        viewModelScope.launch { settings.update { it.copy(key = PhoneRemoteSettings.newKey()) } }
    }
}
