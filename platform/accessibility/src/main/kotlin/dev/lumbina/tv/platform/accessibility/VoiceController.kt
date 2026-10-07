package dev.lumbina.tv.platform.accessibility

import android.os.SystemClock
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.EndOfSpeech
import dev.lumbina.tv.core.domain.speech.RunVoiceSession
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import dev.lumbina.tv.core.domain.speech.VoiceEvent
import dev.lumbina.tv.core.domain.speech.VoiceLanguageMode
import dev.lumbina.tv.core.domain.speech.VoiceLanguageSetting
import dev.lumbina.tv.core.domain.speech.logText
import dev.lumbina.tv.core.domain.voice.HandleUtterance
import dev.lumbina.tv.core.domain.voice.UtteranceOutcome
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Push-to-talk: [start] on mic down, [stop] on mic up, [cancel] when the press was only a
 * tap. [toggle] (on-screen mic keys) listens hands-free instead, until a second of silence after
 * speech or a second tap. Recognition runs in the background; the result goes to [handle] (a command, or text
 * for the focused field → visible field → app keyboard → search) and is shown in the overlay.
 * [cycleLanguage] switches Auto / single-language mode. Call from the main thread.
 */
internal class VoiceController(
    private val scope: CoroutineScope,
    private val sessions: Sessions,
    private val language: VoiceLanguageSetting,
    private val handle: HandleUtterance,
    private val overlay: VoiceOverlay,
    private val strings: Strings,
    private val log: LumbinaLog,
) {
    /** The same recognition, with the capture's drain suited to each way of ending. */
    class Sessions(val pushToTalk: RunVoiceSession, val handsFree: RunVoiceSession)

    /** Overlay texts, from resources. */
    class Strings(
        val listening: String,
        /** The user's own words, set apart from what Lumbina says. */
        val heard: (String) -> String,
        val notHeard: String,
        /** No sound came from the mic at all. */
        val noSound: String,
        val noField: (String) -> String,
        val failed: String,
        val unavailable: (SpeechUnavailable.Reason) -> String,
        val languageMode: (VoiceLanguageMode) -> String,
        val opened: (String) -> String,
        val closed: (String) -> String,
        val appNotFound: (String) -> String,
        val notOpened: (String) -> String,
        val ambiguous: (List<String>) -> String,
        val searched: (String) -> String,
        val sleep: (Int?) -> String,
        val notDone: String,
    )

    @Volatile private var held = false

    @Volatile private var cancelled = false
    private var job: Job? = null

    /** Whether the latest press started the running session (so a tap may cancel it). */
    private var latestPressStartedSession = false

    fun start() = start(endpoint = null)

    /** Listens until [stop], and with an [endpoint] at most until it hears the speech end. */
    private fun start(endpoint: EndOfSpeech?) {
        if (job?.isActive == true) {
            latestPressStartedSession = false // still finishing the previous utterance; leave it alone
            return
        }
        latestPressStartedSession = true
        held = true
        cancelled = false
        overlay.show(strings.listening)
        val events = if (endpoint == null) {
            sessions.pushToTalk { held }
        } else {
            sessions.handsFree.untilSilence(endpoint, wanted = { held })
        }
        job = scope.launch {
            try {
                withContext(Dispatchers.Default) {
                    events.collect { event -> withContext(Dispatchers.Main) { onEvent(event) } }
                }
            } catch (e: CancellationException) {
                throw e // a CancellationException is an IllegalStateException; don't report it
            } catch (e: SpeechUnavailable) {
                log.log(TAG, "failed: $e")
                if (!cancelled) overlay.flash(strings.unavailable(e.reason), UNAVAILABLE_MILLIS)
            } catch (e: IOException) {
                fail(e) // mic source unreachable or closed, or the recognizer failed
            } catch (e: IllegalStateException) {
                fail(e) // speech engine not ready
            } finally {
                held = false // ended by itself (silence, the limit): the next tap starts anew
            }
        }
    }

    fun stop() {
        held = false
    }

    /** The press was a tap, not speech: drop its result quietly. */
    fun cancel() {
        held = false
        if (latestPressStartedSession) {
            cancelled = true
            overlay.hide()
        }
    }

    /** The service is going away: stops listening and takes the overlay down, whatever it showed. */
    fun dispose() {
        held = false
        cancelled = true
        job?.cancel()
        overlay.hide()
    }

    /**
     * Tap-to-talk for on-screen mic keys: starts listening, or stops if already listening. Stops
     * by itself when the speech ends ([EndOfSpeech]), so the second tap is not needed.
     */
    fun toggle() {
        if (held) return stop()
        start(EndOfSpeech(now = SystemClock::elapsedRealtime))
    }

    fun cycleLanguage() {
        val mode = language.cycle()
        log.log(TAG, "language mode: $mode")
        overlay.flash(strings.languageMode(mode), FLASH_MILLIS)
    }

    private fun fail(e: Exception) {
        log.log(TAG, "failed: $e")
        if (!cancelled) overlay.flash(strings.failed, FLASH_MILLIS)
    }

    private suspend fun onEvent(event: VoiceEvent) {
        if (cancelled) return
        when (event) {
            is VoiceEvent.Partial -> overlay.show(
                if (event.text.isEmpty()) strings.listening else strings.heard(event.text),
            )

            is VoiceEvent.Final -> onResult(event)
        }
    }

    private suspend fun onResult(event: VoiceEvent.Final) {
        val stats = event.stats
        val readings = stats.hypotheses.logText()
        val loudest = stats.loudestDb?.let { String.format(Locale.US, "loudest %.0f dB", it) } ?: "no sound"
        log.log(
            TAG,
            "\"${event.result?.text.orEmpty()}\" (${stats.audioMillis} ms audio, $loudest, " +
                "${stats.finishedAfterReleaseMillis} ms after release, $readings)",
        )
        val text = event.result?.text
        if (text == null) {
            // No sound at all points at the mic rather than at how it was said.
            overlay.flash(if (stats.loudestDb == null) strings.noSound else strings.notHeard, FLASH_MILLIS)
            return
        }
        overlay.flash(strings.heard(text), RESULT_MILLIS)
        val outcome = handle(text)
        log.log(TAG, "→ $outcome")
        feedback(outcome)?.let { overlay.flash(it, RESULT_MILLIS) }
    }

    /** What to show after the utterance was handled; null keeps the recognized text on screen. */
    private fun feedback(outcome: UtteranceOutcome): String? = when (outcome) {
        is UtteranceOutcome.Typed, is UtteranceOutcome.Done -> null
        is UtteranceOutcome.NotDelivered -> strings.noField(outcome.text)
        is UtteranceOutcome.Opened -> strings.opened(outcome.app)
        is UtteranceOutcome.Closed -> strings.closed(outcome.app)
        is UtteranceOutcome.AppNotFound -> strings.appNotFound(outcome.query)
        is UtteranceOutcome.NotOpened -> strings.notOpened(outcome.app)
        is UtteranceOutcome.Ambiguous -> strings.ambiguous(outcome.apps)
        is UtteranceOutcome.Searched -> strings.searched(outcome.query)
        is UtteranceOutcome.SleepSet -> strings.sleep(outcome.minutes)
        is UtteranceOutcome.Failed -> strings.notDone
    }

    private companion object {
        const val TAG = "voice"
        const val FLASH_MILLIS = 1_500L
        const val RESULT_MILLIS = 2_500L

        /** Longer: these say what to do. */
        const val UNAVAILABLE_MILLIS = 4_000L
    }
}
