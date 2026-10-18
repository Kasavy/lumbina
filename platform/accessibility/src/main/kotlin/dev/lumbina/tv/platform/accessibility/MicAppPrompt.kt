package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.content.pm.PackageManager
import android.view.KeyEvent
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.asksAboutMic
import dev.lumbina.tv.core.domain.access.withMicApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The card a question is asked on: [CornerCard] on the device. */
internal interface PromptCard {
    /** Which button OK presses. */
    val selected: Int

    /** False when the card could not be put on screen. */
    fun show(message: String, buttons: List<String>, fraction: Float? = null, hint: String? = null): Boolean

    fun move(step: Int)

    fun hide()
}

/**
 * The question about an app that records the microphone itself ([MicInjection] heard it): may it
 * hear the remote? It is asked over the app, for as long as its bar runs, and goes when another app
 * comes to the front. "Not now", the button chosen at first, decides nothing: the question comes
 * again with the app's next recording. The other two answers are kept, and the card says what
 * came of each: the sound being turned on, then that it is on or could not be; or where "never"
 * can be changed.
 *
 * While the card shows, the arrows, OK and Back are its own. A key held since before it showed
 * stays the app's, so a button held to talk doesn't answer the question. Main thread, except [ask].
 */
internal class MicAppPrompt(
    private val card: PromptCard,
    private val texts: Texts,
    private val settings: AccessSettingsRepository,
    private val inFront: () -> String?,
    private val scope: CoroutineScope,
    private val log: LumbinaLog,
) {
    /** The card's words; [label] names an app the way the user knows it. */
    class Texts(
        val question: (String) -> String,
        val later: String,
        val allow: String,
        val never: String,
        val allowed: String,
        val label: suspend (String) -> String,
        val enabling: String = "",
        val failed: String = "",
        val openAccess: String = "",
        val neverNote: String = "",
    )

    /** Whether the sound was turned on for the app just allowed: the start of what sends it. */
    var started: suspend () -> Boolean = { true }

    /** While true the question's time stands still: another card has the keys. */
    var paused: () -> Boolean = { false }

    /** Opens the settings' page where the answers and the connection are. */
    var openAccess: () -> Unit = {}

    private var asking: String? = null

    /** The app a card with no question on it is about: what came of the answer. */
    private var told: String? = null

    /** That card says the sound could not be turned on, and OK leads to where it is put right. */
    private var failed = false
    private var deciding = false
    private var closing: Job? = null

    /** The keys whose press the card took: their repeats and their release are the card's too. */
    private val held = mutableSetOf<Int>()

    /** From any thread. */
    fun ask(app: String) {
        scope.launch {
            if (asking != null || deciding) return@launch
            deciding = true
            try {
                if (!settings.settings.first().asksAboutMic(app, inFront())) return@launch
                val message = texts.question(texts.label(app))
                val buttons = listOf(texts.later, texts.allow, texts.never)
                if (!card.show(message, buttons, fraction = 1f)) return@launch
                asking = app
                told = null
                failed = false
                log.log(TAG, "asking whether $app may hear the remote")
                count(message, buttons)
            } finally {
                deciding = false
            }
        }
    }

    /** Whether a card that takes keys shows: a question, or a failure OK leads on from. */
    val takesKeys: Boolean get() = asking != null || failed

    /** ◀ ▶ pick an answer, OK gives it, Back is "not now"; ▲ ▼ do nothing while the card shows. */
    fun onKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return held.remove(code)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return code in held
        if (failed && event.isOk) {
            held += code
            hide()
            openAccess()
            return true
        }
        val app = asking ?: return false
        if (!event.isOk && code !in OURS) return false
        if (inFront() != app) {
            hide()
            return false
        }
        held += code
        when {
            code == KeyEvent.KEYCODE_DPAD_LEFT -> card.move(-1)
            code == KeyEvent.KEYCODE_DPAD_RIGHT -> card.move(1)
            event.isOk -> answer(app, card.selected)
            code == KeyEvent.KEYCODE_BACK -> hide()
        }
        return true
    }

    /**
     * Another window came to the front: a card about an app that left the screen is taken away,
     * the question and what came of its answer alike.
     */
    fun onWindowChanged() {
        val app = asking ?: told ?: return
        if (inFront() != app) hide()
    }

    fun hide() {
        closing?.cancel()
        asking = null
        told = null
        failed = false
        card.hide()
    }

    private fun answer(app: String, choice: Int) {
        if (choice == LATER) return hide()
        val allowed = choice == ALLOW
        log.log(TAG, "$app ${if (allowed) "may" else "may not"} hear the remote")
        scope.launch { settings.update { it.withMicApp(app, allowed) } }
        asking = null
        told = app
        closing?.cancel()
        closing = scope.launch {
            if (!allowed) {
                card.show(texts.neverNote, emptyList())
                delay(NEVER_MILLIS)
                return@launch hide()
            }
            card.show(texts.enabling, emptyList())
            val on = withTimeoutOrNull(START_MILLIS) { started() } ?: false
            // The recording that raised the question started without the remote: it has to be started again.
            if (on) {
                card.show(texts.allowed, emptyList())
                delay(DONE_MILLIS)
            } else {
                failed = true
                card.show(texts.failed, emptyList(), hint = texts.openAccess)
                delay(FAILED_MILLIS)
            }
            hide()
        }
    }

    /** The question's time runs out on its bar; it stands still while [paused]. */
    private fun count(message: String, buttons: List<String>) {
        closing?.cancel()
        closing = scope.launch {
            var left = ASK_MILLIS
            while (left > 0) {
                delay(TICK_MILLIS)
                if (paused()) continue
                left -= TICK_MILLIS
                card.show(message, buttons, left.toFloat() / ASK_MILLIS)
            }
            hide()
        }
    }

    companion object {
        private const val TAG = "voice"
        private const val LATER = 0
        private const val ALLOW = 1
        const val ASK_MILLIS = 30_000L
        const val DONE_MILLIS = 5_000L
        const val NEVER_MILLIS = 4_000L
        const val FAILED_MILLIS = 6_000L

        /** Longer than the helper is waited for before its start counts as failed. */
        const val START_MILLIS = 15_000L
        private const val TICK_MILLIS = 500L
        private val OURS = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_BACK,
        )

        /** The card's words from the resources, and apps named by their own labels. */
        fun texts(context: Context) = Texts(
            question = { context.getString(R.string.mic_app_question, it) },
            later = context.getString(R.string.mic_app_later),
            allow = context.getString(R.string.mic_app_allow),
            never = context.getString(R.string.mic_app_never),
            allowed = context.getString(R.string.mic_app_allowed),
            label = { app -> withContext(Dispatchers.IO) { context.labelOf(app) } },
            enabling = context.getString(R.string.mic_app_enabling),
            failed = context.getString(R.string.mic_app_failed),
            openAccess = context.getString(R.string.mic_app_open_access),
            neverNote = context.getString(R.string.mic_app_never_note),
        )

        private fun Context.labelOf(app: String): String = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(app, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            app
        }
    }
}
