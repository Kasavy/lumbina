package dev.lumbina.tv.platform.accessibility

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.SpeechEngineChoice
import dev.lumbina.tv.core.domain.speech.SpeechSetup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gets recognition ready for a [SpeechSetup]: chooses its provider and prepares its languages. */
internal class VoiceReadiness(private val engine: SpeechEngineChoice, private val log: LumbinaLog) {

    /** Whether recognition can work now: a model is installed, or a cloud service has its key. */
    suspend fun prepare(setup: SpeechSetup): Boolean {
        engine.choose(setup)
        val ready = withContext(Dispatchers.IO) {
            runCatching { engine.prepare(setup.languages) }.getOrDefault(false)
        }
        val by = engine.provider
        log.log(TAG, if (ready) "voice ready ($by): ${engine.languages}" else "$by can't recognize ${setup.languages}")
        return ready
    }

    private companion object {
        const val TAG = "voice"
    }
}
