package dev.lumbina.tv.core.domain.speech

import kotlinx.coroutines.flow.StateFlow

/** Where a language's speech model is: not there, on its way, or ready to use. */
sealed interface ModelState {
    data object Missing : ModelState

    data class Downloading(val bytes: Long, val totalBytes: Long) : ModelState {
        val fraction: Float get() = if (totalBytes > 0) bytes.toFloat() / totalBytes else 0f
    }

    data object Unpacking : ModelState

    data object Installed : ModelState

    data class Failed(val reason: Reason) : ModelState

    enum class Reason { Network, Corrupt, NoSpace, Storage }
}

/** A language that voice input can recognize once its model is downloaded. */
data class SpeechModelInfo(val language: String, val sizeBytes: Long)

/**
 * Speech models the device can download (they are too big to ship in the APK). Installing
 * runs in the background and survives leaving the screen; the speech engine picks new models
 * up without a restart.
 */
interface SpeechModels {
    val catalog: List<SpeechModelInfo>

    /** State of every language in [catalog]. */
    val states: StateFlow<Map<String, ModelState>>

    /** Downloads and installs [language]; no-op if it is installed or on its way. */
    fun install(language: String)

    /** Cancels a download or removes an installed model. */
    fun delete(language: String)
}
