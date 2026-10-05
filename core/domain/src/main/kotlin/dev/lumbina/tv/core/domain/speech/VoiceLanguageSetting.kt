package dev.lumbina.tv.core.domain.speech

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The persisted [VoiceLanguageMode] as voice input sees it: read without waiting, switched
 * with [cycle] (the mic double tap) and saved in the background. [available] lists the
 * languages recognition can use, preferred first.
 */
class VoiceLanguageSetting(
    private val available: () -> List<String>,
    private val repository: VoiceSettingsRepository,
    private val scope: CoroutineScope,
) {
    @Volatile var mode: VoiceLanguageMode = VoiceLanguageMode.Auto
        private set

    /** Saves in flight; until they land, stored values are older than [mode] and ignored. */
    private val saving = AtomicInteger(0)

    init {
        scope.launch { repository.settings.collect { if (saving.get() == 0) mode = it.mode } }
    }

    /** Languages to recognize right now. */
    fun languages(): List<String> = mode.languages(available())

    /** Switches to the next mode, saves it and returns it. */
    fun cycle(): VoiceLanguageMode {
        val next = mode.next(available())
        mode = next
        saving.incrementAndGet()
        scope.launch {
            // The latest mode, not [next]: quick cycles may save out of order.
            try {
                repository.update { it.copy(mode = mode) }
            } finally {
                saving.decrementAndGet()
            }
        }
        return next
    }
}
