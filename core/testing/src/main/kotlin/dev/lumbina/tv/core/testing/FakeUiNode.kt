package dev.lumbina.tv.core.testing

import dev.lumbina.tv.core.domain.text.ScreenReader
import dev.lumbina.tv.core.domain.text.UiNode

/** In-memory UI node; records clicks, text and submits. */
class FakeUiNode(
    override var text: String? = null,
    override val description: String? = null,
    override val isEditable: Boolean = false,
    override val isClickable: Boolean = false,
    override val isVisible: Boolean = true,
    override val sendsOnSubmit: Boolean = false,
    override val packageName: String? = "app",
    override val children: List<FakeUiNode> = emptyList(),
    private val onClick: (FakeUiNode) -> Unit = {},
) : UiNode {
    var clicks = 0
        private set
    var submitted = false
        private set

    override fun click(): Boolean {
        clicks++
        onClick(this)
        return true
    }

    override fun setText(text: String): Boolean {
        if (!isEditable) return false
        this.text = text
        return true
    }

    override fun submit(): Boolean {
        submitted = true
        return true
    }
}

class FakeScreenReader(var root: UiNode? = null, var focused: UiNode? = null) : ScreenReader {
    override fun focusedInput(): UiNode? = focused

    override fun activeWindow(): UiNode? = root
}
