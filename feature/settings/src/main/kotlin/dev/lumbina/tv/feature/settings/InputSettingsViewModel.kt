package dev.lumbina.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.input.InputSettings
import dev.lumbina.tv.core.domain.input.InputSettingsRepository
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class InputSettingsViewModel @Inject constructor(private val repository: InputSettingsRepository) : ViewModel() {

    val state: StateFlow<InputSettings?> =
        repository.settings.stateIn(viewModelScope, WhileUiSubscribed, null)

    fun toggleLumbinaKeyboard() {
        viewModelScope.launch { repository.update { it.copy(useLumbinaKeyboard = !it.useLumbinaKeyboard) } }
    }
}
