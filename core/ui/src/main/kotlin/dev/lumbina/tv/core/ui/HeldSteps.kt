package dev.lumbina.tv.core.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.ModifierNodeElement

/**
 * A held ▲ or ▼ in a list of rows steps at most every [HeldStepPacer.STEP_MILLIS]; a press of its
 * own always goes through. Only where layers are dear ([LAYERS_ARE_CHEAP] false), the weak boxes:
 * a held key repeats about 20 times a second, each step starts a scroll and may place a row, more
 * than such a box draws. Elsewhere a held key keeps its full pace.
 */
fun Modifier.paceHeldSteps(): Modifier = if (LAYERS_ARE_CHEAP) this else this then PaceHeldSteps

/** Which presses of a held key go through: the first always, a repeat once [STEP_MILLIS] passed since the last. */
class HeldStepPacer {
    private var last = 0L

    fun passes(repeatCount: Int, eventTime: Long): Boolean {
        if (repeatCount > 0 && eventTime - last < STEP_MILLIS) return false
        last = eventTime
        return true
    }

    companion object {
        /** Under two repeats of a held key (50 ms each), so every second one goes, however they jitter. */
        const val STEP_MILLIS = 90L
    }
}

private data object PaceHeldSteps : ModifierNodeElement<PaceHeldStepsNode>() {
    override fun create() = PaceHeldStepsNode()

    override fun update(node: PaceHeldStepsNode) = Unit
}

private class PaceHeldStepsNode :
    Modifier.Node(),
    KeyInputModifierNode {
    private val pacer = HeldStepPacer()

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        val vertical = event.key == Key.DirectionDown || event.key == Key.DirectionUp
        if (event.type != KeyEventType.KeyDown || !vertical) return false
        val native = event.nativeKeyEvent
        return !pacer.passes(native.repeatCount, native.eventTime)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}
