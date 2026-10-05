package dev.lumbina.tv.core.domain.text

/** A node of the on-screen UI tree (backed by AccessibilityNodeInfo on Android). */
interface UiNode {
    val packageName: String?
    val text: String?
    val description: String?
    val isEditable: Boolean
    val isClickable: Boolean
    val isVisible: Boolean

    /**
     * A field whose IME action would send something to someone or somewhere for good: a message,
     * multi-line text, a password. Search boxes and the like are not.
     */
    val sendsOnSubmit: Boolean get() = false
    val children: List<UiNode>

    fun click(): Boolean

    fun setText(text: String): Boolean

    /** Presses the IME action (search/go/done) on an editable node. */
    fun submit(): Boolean
}

/** Access to what is on screen right now. */
interface ScreenReader {
    /** The node that has input focus, if any. */
    fun focusedInput(): UiNode?

    /** Root of the window the user is interacting with. */
    fun activeWindow(): UiNode?
}

/** Breadth-first walk over a UI tree. */
fun UiNode.walk(): Sequence<UiNode> = sequence {
    val queue = ArrayDeque<UiNode>()
    queue.add(this@walk)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        yield(node)
        queue.addAll(node.children)
    }
}

/** The label a user sees on a node: its text or, for icons, its content description. */
val UiNode.label: String
    get() = (text ?: description).orEmpty().trim()
