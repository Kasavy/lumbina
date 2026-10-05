package dev.lumbina.tv.platform.accessibility

import android.os.SystemClock
import dev.lumbina.tv.core.domain.speech.AudioSource
import dev.lumbina.tv.core.domain.speech.PcmCapture
import dev.lumbina.tv.core.domain.speech.RunVoiceSession
import dev.lumbina.tv.core.domain.speech.SpeechEngine
import kotlinx.coroutines.Dispatchers

/**
 * Builds every [RunVoiceSession] of the service: the same recognition and clock for all, the
 * capture's drain suited to each way of ending.
 */
internal class VoiceSessions(private val engine: SpeechEngine) {

    /** The languages recognition has now. */
    val languages: List<String> get() = engine.languages

    /** Ended by the mic key's release. */
    fun pushToTalk(audio: AudioSource, languages: () -> List<String>): RunVoiceSession =
        session(audio, PUSH_TO_TALK_DRAIN_MILLIS, languages)

    /** Ended by silence ([RunVoiceSession.untilSilence]). */
    fun handsFree(audio: AudioSource, languages: () -> List<String>): RunVoiceSession =
        session(audio, HANDS_FREE_DRAIN_MILLIS, languages)

    /** A recording, which ends by itself; in every language recognition has. */
    fun recording(audio: AudioSource): RunVoiceSession = session(audio, PUSH_TO_TALK_DRAIN_MILLIS) { engine.languages }

    private fun session(audio: AudioSource, drainMaxMillis: Long, languages: () -> List<String>) = RunVoiceSession(
        capture = PcmCapture(audio, now = SystemClock::elapsedRealtime, drainMaxMillis = drainMaxMillis),
        engine = engine,
        now = SystemClock::elapsedRealtime,
        captureDispatcher = Dispatchers.IO,
        languages = languages,
    )

    private companion object {
        /** The remote delivers its audio with a lag: keep reading a while after the key is up. */
        const val PUSH_TO_TALK_DRAIN_MILLIS = 1_500L

        /** Hands-free listening ends after a second of silence already, so little is left to drain. */
        const val HANDS_FREE_DRAIN_MILLIS = 300L
    }
}
