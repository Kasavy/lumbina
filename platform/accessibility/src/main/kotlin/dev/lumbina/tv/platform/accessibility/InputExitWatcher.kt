package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.device.InputSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Leaving an input shown full screen comes back to Lumbina. Back on the input's screen with nothing
 * over the picture to choose from (no menu) is taken: Android's picture comes back and Lumbina opens.
 * The device's own home screen coming up right after the input's screen gets Lumbina over it; one
 * opened from Lumbina's quick menu or by the user stays. Main thread.
 */
internal class InputExitWatcher(
    private val inputs: InputSources,
    private val isHomeScreen: (String, String?) -> Boolean,
    /** Lumbina's own package: one of its screens coming up is a way out of the input already. */
    private val own: String,
    /** The window that has the keys now. */
    private val front: () -> AccessibilityNodeInfo?,
    private val open: () -> Unit,
    private val log: LumbinaLog,
) {
    private var scope: CoroutineScope? = null

    /** Whether the last window that counts was the input's screen. */
    private var onInput = false

    /** Back went down on the bare input screen: its release is taken too, and then Lumbina opens. */
    private var backTaken = false

    fun start(scope: CoroutineScope) {
        this.scope = scope
    }

    /** True if the key is taken. */
    fun onKey(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) backTaken = bareInputScreen()
                backTaken
            }

            KeyEvent.ACTION_UP -> backTaken.also { taken ->
                if (taken) {
                    backTaken = false
                    leave()
                }
            }

            else -> false
        }
    }

    /** The user opened another screen on purpose (the device's own home screen): it stays. */
    fun leftByUser() {
        onInput = false
    }

    /** A window ([className] of [packageName]) came to the front. */
    fun onWindow(packageName: CharSequence?, className: CharSequence?) {
        val name = packageName?.toString() ?: return
        when {
            inputs.showsInputs(name) -> onInput = true

            // A screen of Lumbina's, not the quick menu over the input, which is a window of no activity.
            name == own && className?.startsWith(own) == true -> onInput = false

            onInput && isHomeScreen(name, className?.toString()) -> {
                onInput = false
                log.log(TAG, "the firmware's home screen came up after an input: opening Lumbina")
                open()
            }
        }
    }

    /**
     * The input's screen has the keys and shows nothing to choose from: Back there would leave it.
     * Asked of the window only after an input's screen came up: Back in any other app costs nothing.
     */
    private fun bareInputScreen(): Boolean {
        if (!onInput) return false
        val root = front() ?: return false
        if (root.packageName?.toString()?.let(inputs::showsInputs) != true) return false
        return !root.hasFocusable()
    }

    @Suppress("DEPRECATION") // recycling still frees the nodes below Android 13
    private fun AccessibilityNodeInfo.hasFocusable(): Boolean {
        if (isFocusable && isVisibleToUser) return true
        return (0 until childCount).any { i ->
            val child = getChild(i) ?: return@any false
            try {
                child.hasFocusable()
            } finally {
                child.recycle()
            }
        }
    }

    private fun leave() {
        log.log(TAG, "Back on an input: back to Android's picture and Lumbina")
        onInput = false
        scope?.launch {
            inputs.showAndroid()
            open()
        }
    }

    private companion object {
        const val TAG = "inputs"
    }
}
