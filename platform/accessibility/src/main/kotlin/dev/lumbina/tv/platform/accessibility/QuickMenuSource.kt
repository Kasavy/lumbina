package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import dev.lumbina.tv.core.domain.device.InputCatalog
import dev.lumbina.tv.core.domain.device.InputSource
import dev.lumbina.tv.core.domain.device.InputSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The quick menu's video input: the arrows go through Android's own picture ("Smart TV") and the
 * inputs, stopping at either end, and OK switches to the one they came to. Main thread.
 */
internal class QuickMenuSource(
    private val service: AccessibilityService,
    private val sources: InputSources,
    private val catalog: InputCatalog,
    private val say: (String) -> Unit,
) {
    private var scope: CoroutineScope? = null
    private var listening: Job? = null

    /** Under the user's names. */
    private var inputs = emptyList<InputSource>()

    /** The chosen input and the one the arrows came to; null for Android's own picture. */
    private var current: String? = null
    private var picked: String? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
        listen(scope)
    }

    private fun listen(scope: CoroutineScope) {
        listening?.cancel()
        listening = scope.launch { catalog.inputs.collect { inputs = it } }
    }

    /**
     * The menu opened: the row starts from the input chosen now, which the remote's own button may
     * have changed since; [changed] once that is known, unless the arrows have moved on meanwhile.
     */
    fun shown(changed: () -> Unit) {
        picked = current
        if (inputs.isEmpty()) {
            // The device's input service may not have answered at boot: asked again, the row comes next time.
            scope?.let(::listen)
            return
        }
        scope?.launch {
            val before = picked
            // An input that is not among the inputs (a screen of the device's own) counts as Android's picture.
            current = sources.current()?.takeIf { id -> inputs.any { it.id == id } }
            if (picked == before) {
                picked = current
                changed()
            }
        }
    }

    /** The row; none where the device has no inputs. */
    fun item(): QuickMenuOverlay.Item? {
        if (inputs.isEmpty()) return null
        return QuickMenuOverlay.Item(
            label = service.getString(R.string.quick_source),
            value = { "◀ ${nameOf(picked)} ▶" },
            onAdjust = { step -> picked = stepInput(inputs.map { it.id }, picked, step) },
        ) { switchTo(picked) }
    }

    private fun nameOf(id: String?): String =
        inputs.firstOrNull { it.id == id }?.name ?: service.getString(R.string.quick_smart_tv)

    /** To [id], or for null back to Android's own picture with Lumbina on it: the device's Home may be another's. */
    private fun switchTo(id: String?) {
        // Back to Android's picture goes even when it seems chosen: not every device tells the input it is on.
        if (id != null && id == current) return
        scope?.launch {
            if (id == null) {
                sources.showAndroid()
                service.openLumbinaHome()
            } else if (!catalog.open(id)) {
                say(service.getString(R.string.quick_input_failed, nameOf(id)))
            }
        }
    }
}

/**
 * The input [by] steps from [from] among Android's own picture (null, the first) and [inputs],
 * stopping at either end.
 */
internal fun stepInput(inputs: List<String>, from: String?, by: Int): String? {
    val choices = listOf<String?>(null) + inputs
    return choices[(choices.indexOf(from).coerceAtLeast(0) + by).coerceIn(0, choices.lastIndex)]
}
