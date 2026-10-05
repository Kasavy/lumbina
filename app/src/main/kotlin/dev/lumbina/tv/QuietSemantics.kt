package dev.lumbina.tv

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.platform.ViewRootForTest
import dev.lumbina.tv.core.common.LumbinaLog

/**
 * While the only accessibility service on is Lumbina's own, spares Compose its per-frame
 * accessibility bookkeeping: that service reads the screen only when it delivers text, and such
 * reads still get a fresh tree. Done by emptying Compose's cached list of enabled services, which
 * Compose clears itself when accessibility changes; with another service on (TalkBack) nothing is
 * touched. Not API: if the field can't be reached nothing changes (names kept in proguard-rules.pro).
 */
internal class QuietSemantics(private val context: Context, private val log: LumbinaLog) {

    private val manager = context.getSystemService(AccessibilityManager::class.java)
    private var root: View? = null

    /**
     * Compose clears the list on these changes, in its own listener; listeners run in no set
     * order, so quiet it again once they all have.
     */
    private val onStateChange = AccessibilityManager.AccessibilityStateChangeListener { root?.post { apply() } }

    /** While Lumbina is on screen; call from onResume. */
    fun start(root: View) {
        this.root = root
        manager.addAccessibilityStateChangeListener(onStateChange)
        // After the first layout, once Compose has its view.
        root.post { apply() }
    }

    fun stop() {
        manager.removeAccessibilityStateChangeListener(onStateChange)
        root = null
    }

    private fun apply() {
        val root = root ?: return
        val quiet = onlyOurs()
        composeViews(root).forEach { view -> setEnabledServices(view, if (quiet) emptyList() else null) }
    }

    /** Whether every enabled service is Lumbina's own (and there is one). */
    private fun onlyOurs(): Boolean {
        if (!manager.isEnabled) return false
        val services = manager.getEnabledAccessibilityServiceList(FEEDBACK_ALL)
        return services.isNotEmpty() && services.all { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    private fun composeViews(view: View): List<View> = when {
        view is ViewRootForTest -> listOf(view)
        view is ViewGroup -> (0 until view.childCount).flatMap { composeViews(view.getChildAt(it)) }
        else -> emptyList()
    }

    /** [services] null makes Compose ask the system again. */
    private fun setEnabledServices(view: View, services: List<Any>?) {
        try {
            val delegate = view.javaClass.getDeclaredField(DELEGATE).apply { isAccessible = true }.get(view)
            delegate.javaClass.getDeclaredField(SERVICES).apply { isAccessible = true }.set(delegate, services)
        } catch (e: ReflectiveOperationException) {
            if (!reported) log.log(TAG, "can't quiet Compose's accessibility: $e")
            reported = true
        }
    }

    internal companion object {
        private const val TAG = "ui"
        private const val FEEDBACK_ALL = AccessibilityServiceInfo.FEEDBACK_ALL_MASK

        /** Compose's names (QuietSemanticsTest checks they are still there). */
        const val DELEGATE = "composeAccessibilityDelegate"
        const val SERVICES = "_enabledServices"

        @Volatile private var reported = false
    }
}
