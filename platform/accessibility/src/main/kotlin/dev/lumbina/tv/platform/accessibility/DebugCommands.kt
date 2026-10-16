package dev.lumbina.tv.platform.accessibility

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.text.DeliverText
import dev.lumbina.tv.core.domain.text.walk
import dev.lumbina.tv.core.domain.voice.HandleUtterance
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Debug builds only, for testing without a remote (injected key events don't reach
 * accessibility key filters). Only senders holding DUMP, such as the adb shell, get through:
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.DELIVER_TEXT --es text "котики"`
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.DUMP_TREE` → logs the active window's nodes.
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.SWITCHER --es key show|left|right|ok|back|list`
 *   → drives the recent-apps switcher.
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.QUICK_MENU --es key show|up|down|left|right|ok|back`
 *   → drives the quick menu.
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.SLEEP_IN --ei seconds 70` → a short sleep timer.
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.SAY --es text "открой кинопоиск"` → as if spoken:
 *   a command or typed text, answered on the voice plate.
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.MIC --es key down|up|tap` → the remote's mic key,
 *   so what its microphone gives is heard.
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.KEY --ei code 82 --es key down|up` → a button of
 *   the remote, by its Android key code, as the service would see it.
 * - `adb shell am broadcast -a dev.lumbina.tv.debug.HEAR` → recognizes `files/hear.wav` in the app's
 *   external storage (16 kHz mono 16-bit, pushed with adb) and handles it as if spoken.
 */
internal class DebugCommands(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val deliver: () -> DeliverText,
    private val say: () -> HandleUtterance,
    /** Shows what came of a said phrase as the voice plate would, and takes the plate's keys. */
    private val answers: VoiceAnswers,
    private val hear: suspend (File) -> String?,
    private val switcher: (String) -> Unit,
    private val quickMenu: (String) -> Unit,
    private val sleepIn: (Int) -> Unit,
    private val log: LumbinaLog,
) {
    /** Presses the remote's mic key; set once the key's handling is built. */
    var mic: (String) -> Unit = {}

    /**
     * Presses a button of the remote as the service would see it (keys sent over adb go past the
     * service): a key code, and "down" or "up". Set once the keys' handling is built.
     */
    var key: (Int, String) -> Unit = { _, _ -> }

    /** The keys of the plate a said phrase left on screen; true if taken. */
    fun onKey(event: KeyEvent): Boolean = answers.onKey(event)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_DELIVER_TEXT -> scope.launch {
                    val text = intent.getStringExtra("text").orEmpty()
                    log.log(TAG, "deliver \"$text\" via ${deliver()(text) ?: "nothing"}")
                }

                ACTION_SAY -> scope.launch {
                    val text = intent.getStringExtra("text").orEmpty()
                    val outcome = say()(text)
                    log.log(TAG, "say \"$text\" → $outcome")
                    answers.report(outcome)
                }

                ACTION_HEAR -> scope.launch {
                    val file = File(service.getExternalFilesDir(null), "hear.wav")
                    val started = SystemClock.elapsedRealtime()
                    val text = runCatching { hear(file) }.getOrElse { return@launch log.log(TAG, "hear: $it") }
                    log.log(TAG, "heard \"${text.orEmpty()}\" in ${SystemClock.elapsedRealtime() - started} ms")
                    if (text != null) log.log(TAG, "→ ${say()(text)}")
                }

                ACTION_MIC -> mic(intent.getStringExtra("key").orEmpty())

                ACTION_KEY -> key(intent.getIntExtra("code", 0), intent.getStringExtra("key").orEmpty())

                ACTION_SWITCHER -> switcher(intent.getStringExtra("key").orEmpty())

                ACTION_QUICK_MENU -> quickMenu(intent.getStringExtra("key").orEmpty())

                ACTION_SLEEP_IN -> sleepIn(intent.getIntExtra("seconds", DEFAULT_SLEEP_SECONDS))

                ACTION_DUMP_TREE -> AccessibilityScreenReader(service).activeWindow()?.walk()?.forEach {
                    val flags = listOfNotNull(
                        "editable".takeIf { _ -> it.isEditable },
                        "clickable".takeIf { _ -> it.isClickable },
                        "hidden".takeIf { _ -> !it.isVisible },
                    )
                    log.log(TAG, "text=\"${it.text.orEmpty()}\" desc=\"${it.description.orEmpty()}\" $flags")
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_DELIVER_TEXT)
            addAction(ACTION_DUMP_TREE)
            addAction(ACTION_SWITCHER)
            addAction(ACTION_QUICK_MENU)
            addAction(ACTION_SLEEP_IN)
            addAction(ACTION_SAY)
            addAction(ACTION_HEAR)
            addAction(ACTION_MIC)
            addAction(ACTION_KEY)
        }
        ContextCompat.registerReceiver(
            service,
            receiver,
            filter,
            Manifest.permission.DUMP,
            null,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    fun unregister() {
        service.unregisterReceiver(receiver)
    }

    private companion object {
        const val TAG = "debug"
        const val ACTION_DELIVER_TEXT = "dev.lumbina.tv.debug.DELIVER_TEXT"
        const val ACTION_DUMP_TREE = "dev.lumbina.tv.debug.DUMP_TREE"
        const val ACTION_SWITCHER = "dev.lumbina.tv.debug.SWITCHER"
        const val ACTION_QUICK_MENU = "dev.lumbina.tv.debug.QUICK_MENU"
        const val ACTION_SLEEP_IN = "dev.lumbina.tv.debug.SLEEP_IN"
        const val ACTION_SAY = "dev.lumbina.tv.debug.SAY"
        const val ACTION_HEAR = "dev.lumbina.tv.debug.HEAR"
        const val ACTION_MIC = "dev.lumbina.tv.debug.MIC"
        const val ACTION_KEY = "dev.lumbina.tv.debug.KEY"
        const val DEFAULT_SLEEP_SECONDS = 70
    }
}
