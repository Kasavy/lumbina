package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import dev.lumbina.tv.core.domain.text.ScreenReader
import dev.lumbina.tv.core.domain.text.UiNode

/** [ScreenReader] backed by the accessibility service's view of the screen. */
internal class AccessibilityScreenReader(private val service: AccessibilityService) : ScreenReader {

    override fun focusedInput(): UiNode? = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.let(::AndroidUiNode)

    override fun activeWindow(): UiNode? = service.rootInActiveWindow?.let(::AndroidUiNode)
}

internal class AndroidUiNode(private val node: AccessibilityNodeInfo) : UiNode {
    override val packageName: String? get() = node.packageName?.toString()
    override val text: String? get() = node.text?.toString()
    override val description: String? get() = node.contentDescription?.toString()
    override val isEditable: Boolean get() = node.isEditable
    override val isClickable: Boolean get() = node.isClickable
    override val isVisible: Boolean get() = node.isVisibleToUser

    override val sendsOnSubmit: Boolean get() = sendsOnSubmit(node.inputType)
    override val children: List<UiNode>
        get() = (0 until node.childCount).mapNotNull { node.getChild(it)?.let(::AndroidUiNode) }

    override fun click(): Boolean = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

    override fun setText(text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    override fun submit(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
}

/**
 * Whether a field of this [inputType] would send for good on its IME action. Accessibility shows
 * the input type but not the action, so it is told by the type: multi-line text, a message, or a
 * password (text or a numeric PIN).
 */
internal fun sendsOnSubmit(inputType: Int): Boolean {
    val kind = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (kind) {
        InputType.TYPE_CLASS_TEXT -> inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0 || variation in TEXT_SENDING
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

private val TEXT_SENDING = setOf(
    InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
    InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
    InputType.TYPE_TEXT_VARIATION_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
)
