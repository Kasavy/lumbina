package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.voice.UtteranceOutcome
import dev.lumbina.tv.platform.accessibility.VoiceController.Place
import dev.lumbina.tv.platform.accessibility.VoiceOverlay.Mark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the plate says once an utterance was dealt with: what came of it, in words; what went wrong,
 * with the place OK leads to; or, where several apps fit a name, those apps to choose from. Main thread.
 */
internal class VoiceAnswers(
    private val scope: CoroutineScope,
    private val overlay: VoiceOverlay,
    private val strings: VoiceController.Strings,
    private val log: LumbinaLog,
) {
    /** Something that went wrong, on screen long enough to be read; with a [place], OK opens it. */
    fun notice(text: String, place: Place? = null) {
        overlay.flash(text, toRead(text), Mark.Failed, place?.let(strings.go))
    }

    /** Says what came of an utterance; where several apps fit, asks which and says what came of the choice. */
    fun report(outcome: UtteranceOutcome) {
        log.log(TAG, "→ $outcome")
        if (outcome is UtteranceOutcome.Ambiguous) return choose(outcome)
        val said = strings.feedback(outcome) ?: return
        when (outcome) {
            is UtteranceOutcome.Opened, is UtteranceOutcome.Closed, is UtteranceOutcome.Searched,
            is UtteranceOutcome.SleepSet,
            -> overlay.flash(said, toRead(said), Mark.Done)

            UtteranceOutcome.SearchFailed -> notice(said, Place.Widgets)

            is UtteranceOutcome.NotOpened -> notice(said, Place.Home)

            UtteranceOutcome.CantClose -> notice(said, Place.Access)

            else -> notice(said)
        }
    }

    private fun choose(outcome: UtteranceOutcome.Ambiguous) {
        val apps = outcome.apps
        if (apps.size > MOST_CHOICES) return notice(strings.tooMany)
        overlay.choose(strings.which, apps.map { it.label }, CHOICE_MILLIS) { index ->
            scope.launch { report(outcome.pick(apps[index])) }
        }
    }

    /** The plate's own keys while it asks or offers something; true if taken. */
    fun onKey(event: KeyEvent): Boolean = overlay.onKey(event)

    companion object {
        private const val TAG = "voice"

        /** More apps than this are not offered as buttons: the name is asked for again. */
        private const val MOST_CHOICES = 4

        /** How long a choice waits for a button. */
        private const val CHOICE_MILLIS = 8_000L

        /** No line is on screen for less. */
        private const val SHORTEST_MILLIS = 2_500L

        /** A line stays long enough to be read: by its length, at a TV's reading pace. */
        private const val MILLIS_PER_CHAR = 60L

        /** One rule for every line the plate shows and takes away. */
        fun toRead(text: String): Long = maxOf(SHORTEST_MILLIS, text.length * MILLIS_PER_CHAR)
    }
}

/** What to show after the utterance was handled; null keeps the recognized text on screen. */
private fun VoiceController.Strings.feedback(outcome: UtteranceOutcome): String? = when (outcome) {
    is UtteranceOutcome.Typed, is UtteranceOutcome.Done, is UtteranceOutcome.Ambiguous -> null
    is UtteranceOutcome.NotDelivered -> noField(outcome.text)
    UtteranceOutcome.SearchFailed -> searchFailed
    is UtteranceOutcome.Opened -> opened(outcome.app)
    is UtteranceOutcome.Closed -> closed(outcome.app)
    is UtteranceOutcome.AppNotFound -> appNotFound(outcome.query)
    is UtteranceOutcome.NotOpened -> notOpened(outcome.app)
    UtteranceOutcome.NothingToClose -> nothingToClose
    UtteranceOutcome.CantClose -> cantClose
    UtteranceOutcome.NoTimer -> noTimer
    is UtteranceOutcome.Searched -> searched(outcome.query)
    is UtteranceOutcome.SleepSet -> sleep(outcome.minutes)
    is UtteranceOutcome.Failed -> notDone
}
