package dev.lumbina.tv.platform.accessibility

import android.os.SystemClock
import android.view.KeyEvent
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.EndOfSpeech
import dev.lumbina.tv.core.domain.speech.RunVoiceSession
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import dev.lumbina.tv.core.domain.speech.VoiceEvent
import dev.lumbina.tv.core.domain.speech.VoiceLanguageMode
import dev.lumbina.tv.core.domain.speech.VoiceLanguageSetting
import dev.lumbina.tv.core.domain.speech.logText
import dev.lumbina.tv.core.domain.voice.HandleUtterance
import dev.lumbina.tv.platform.accessibility.VoiceOverlay.Mark
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val answers = VoiceAnswers(scope, overlay, strings, log)

    /** The same recognition, with the capture's drain suited to each way of ending. */
    class Sessions(val pushToTalk: RunVoiceSession, val handsFree: RunVoiceSession)

    /** Overlay texts, from resources. */
    class Strings(
        val listening: String,
        /** The button is let go and the phrase is being worked out. */
        val recognizing: String = "",
        /** The button was pressed again before that was done. */
        val stillRecognizing: String = "",
        /** The recognizer is not there to hear. */
        val notStarted: String = "",
        /** What OK does on a notice that names a place: opens it. Null where nothing leads there. */
        val go: (Place) -> VoiceOverlay.Action? = { null },
        /** The user's own words, set apart from what Lumbina says. */
        val heard: (String) -> String,
        val notHeard: String,
        /** Nobody spoke. */
        val silence: String = "",
        /** No sound came from the mic at all. */
        val noSound: String,
        /** Why no sound came, when the device can tell (the mic turned off in the firmware), and where to mend it. */
        val micOff: () -> String?,
        val noField: (String) -> String,
        val searchFailed: String,
        val failed: String,
        val unavailable: (SpeechUnavailable) -> String,
        val languageMode: (VoiceLanguageMode) -> String,
        val opened: (String) -> String,
        val closed: (String) -> String,
        val appNotFound: (String) -> String,
        val notOpened: (String) -> String,
        /** The question over a few apps to choose from, and what is said when there are too many to offer. */
        val which: String = "",
        val tooMany: String = "",
        val nothingToClose: String = "",
        val cantClose: String = "",
        val noTimer: String = "",
        /** Asked to switch the language where there is one: said with its name. */
        val oneLanguage: (String?) -> String = { "" },
        val searched: (String) -> String,
        val sleep: (Int?) -> String,
        val notDone: String,
    )

    /** Where a notice sends the user: OK opens it while the notice shows. */
    enum class Place { Diagnostics, Voice, Phone, Access, Widgets, Home }

    @Volatile private var held = false

    /** The button is let go: what is heard from now on is not shown, the plate says it is being worked out. */
    @Volatile private var working = false

    @Volatile private var cancelled = false
    private var job: Job? = null

    /** Whether the latest press started the running session (so a tap may cancel it). */
    private var latestPressStartedSession = false

    /** Listens until [stop], and with an [endpoint] at most until it hears the speech end. */
    fun start(endpoint: EndOfSpeech? = null) {
        if (job?.isActive == true) {
            latestPressStartedSession = false // still finishing the previous utterance; leave it alone
            // Said, or the press would seem lost; then back to what the plate was saying.
            if (!cancelled) {
                overlay.show(strings.stillRecognizing, Mark.Working)
                scope.launch {
                    delay(STILL_MILLIS)
                    if (working && job?.isActive == true && !cancelled) overlay.show(strings.recognizing, Mark.Working)
                }
            }
            return
        }
        latestPressStartedSession = true
        held = true
        working = false
        cancelled = false
        overlay.show(strings.listening, Mark.Listening)
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
                if (!cancelled) answers.notice(strings.unavailable(e), placeOf(e.reason))
            } catch (e: IOException) {
                // The mic source unreachable or closed, or the recognizer failed: which one is not known here.
                fail(e, strings.failed, null)
            } catch (e: IllegalStateException) {
                fail(e, strings.notStarted, Place.Voice) // speech engine not ready
            } finally {
                held = false // ended by itself (silence, the limit): the next tap starts anew
                working = false
            }
        }
    }

    fun stop() {
        held = false
        if (job?.isActive == true && !cancelled && !working) {
            working = true
            overlay.show(strings.recognizing, Mark.Working)
        }
    }

    /** A notice with something OK does has the OK while it shows; true if taken. */
    fun onKey(event: KeyEvent): Boolean = overlay.onKey(event)

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
        val before = language.languages()
        val mode = language.cycle()
        log.log(TAG, "language mode: $mode")
        // With one language there is nothing to switch to: said, with the way to where more are added.
        val text = if (before.size < 2) strings.oneLanguage(before.firstOrNull()) else strings.languageMode(mode)
        overlay.flash(text, VoiceAnswers.toRead(text), action = strings.go(Place.Voice).takeIf { before.size < 2 })
    }

    private fun fail(e: Exception, text: String, place: Place?) {
        log.log(TAG, "failed: $e")
        if (!cancelled) answers.notice(text.ifEmpty { strings.failed }, place)
    }

    private suspend fun onEvent(event: VoiceEvent) {
        if (cancelled) return
        when (event) {
            // Once the button is let go the plate says the phrase is being worked out, not what is heard.
            is VoiceEvent.Partial -> if (!working) {
                overlay.show(
                    if (event.text.isEmpty()) strings.listening else strings.heard(event.text),
                    Mark.Listening,
                )
            }

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
            val off = if (stats.loudestDb == null) strings.micOff() else null
            when {
                off != null -> answers.notice(off, Place.Diagnostics)
                stats.loudestDb == null -> answers.notice(strings.noSound)
                !stats.voiceHeard -> answers.notice(strings.silence)
                else -> answers.notice(strings.notHeard)
            }
            return
        }
        strings.heard(text).let { overlay.flash(it, VoiceAnswers.toRead(it), Mark.Done) }
        answers.report(handle(text))
    }

    private companion object {
        const val TAG = "voice"

        /** How long "still recognizing" answers a press before the plate says again what it did. */
        const val STILL_MILLIS = 1_000L
    }
}

private fun placeOf(reason: SpeechUnavailable.Reason): VoiceController.Place? = when (reason) {
    // A key is pasted from the phone's page: the remote can't type one.
    SpeechUnavailable.Reason.NoKey, SpeechUnavailable.Reason.KeyRefused -> VoiceController.Place.Phone

    SpeechUnavailable.Reason.Region -> VoiceController.Place.Voice

    SpeechUnavailable.Reason.MicNotGiven -> VoiceController.Place.Access

    else -> null
}
