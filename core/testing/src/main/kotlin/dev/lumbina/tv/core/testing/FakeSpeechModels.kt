package dev.lumbina.tv.core.testing

import dev.lumbina.tv.core.domain.speech.ModelState
import dev.lumbina.tv.core.domain.speech.SpeechModelInfo
import dev.lumbina.tv.core.domain.speech.SpeechModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Installs instantly unless [instant] is false, in which case installs stay [ModelState.Downloading]. */
class FakeSpeechModels(
    override val catalog: List<SpeechModelInfo>,
    installed: Set<String> = emptySet(),
    private val instant: Boolean = true,
) : SpeechModels {
    private val mutableStates = MutableStateFlow(
        catalog.associate { it.language to if (it.language in installed) ModelState.Installed else ModelState.Missing },
    )
    val installs = mutableListOf<String>()
    val deletes = mutableListOf<String>()

    override val states: StateFlow<Map<String, ModelState>> = mutableStates

    override fun install(language: String) {
        if (mutableStates.value[language] == ModelState.Installed) return
        installs += language
        val state = if (instant) ModelState.Installed else ModelState.Downloading(0, 1)
        mutableStates.update { it + (language to state) }
    }

    override fun delete(language: String) {
        deletes += language
        mutableStates.update { it + (language to ModelState.Missing) }
    }
}
