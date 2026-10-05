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

/** The card a question is asked on: [CornerCard] on the device. */
internal interface PromptCard {
    /** Which button OK presses. */
    val selected: Int

    /** False when the card could not be put on screen. */
    fun show(message: String, buttons: List<String>, fraction: Float? = null): Boolean

    fun move(step: Int)

    fun hide()
}

/**
 * The question about an app that records the microphone itself ([MicInjection] heard it): may it
 * hear the remote? It is asked over the app and goes when another app comes to the front. "Not
 * now", the button chosen at first, decides nothing: the question comes again with the app's
 * next recording. The other two answers are kept, and can be changed in the settings.
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
    )

    private var asking: String? = null
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
                if (!card.show(message, listOf(texts.later, texts.allow, texts.never))) return@launch
                asking = app
                log.log(TAG, "asking whether $app may hear the remote")
                closeIn(ASK_MILLIS)
            } finally {
                deciding = false
            }
        }
    }

    /** ◀ ▶ pick an answer, OK gives it, Back is "not now"; ▲ ▼ do nothing while the card shows. */
    fun onKey(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return held.remove(code)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (event.repeatCount > 0) return code in held
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

    /** Another window came to the front: a question about an app that left the screen is taken away. */
    fun onWindowChanged() {
        val app = asking ?: return
        if (inFront() != app) hide()
    }

    fun hide() {
        closing?.cancel()
        asking = null
        card.hide()
    }

    private fun answer(app: String, choice: Int) {
        if (choice == LATER) return hide()
        val allowed = choice == ALLOW
        log.log(TAG, "$app ${if (allowed) "may" else "may not"} hear the remote")
        scope.launch { settings.update { it.withMicApp(app, allowed) } }
        if (!allowed) return hide()
        // The recording that raised the question started without the remote: it has to be started again.
        asking = null
        if (card.show(texts.allowed, emptyList())) closeIn(DONE_MILLIS)
    }

    private fun closeIn(millis: Long) {
        closing?.cancel()
        closing = scope.launch {
            delay(millis)
            hide()
        }
    }

    companion object {
        private const val TAG = "voice"
        private const val LATER = 0
        private const val ALLOW = 1
        const val ASK_MILLIS = 30_000L
        const val DONE_MILLIS = 5_000L
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
        )

        private fun Context.labelOf(app: String): String = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(app, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            app
        }
    }
}
