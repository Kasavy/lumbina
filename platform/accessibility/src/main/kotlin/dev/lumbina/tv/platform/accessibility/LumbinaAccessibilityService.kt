package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import dev.lumbina.tv.core.common.LumbinaLog
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * Lumbina's always-running part: takes over the remote's Home and mic buttons from the firmware
 * (the mic as push-to-talk voice input), shows the app switcher, quick menu and sleep warning
 * over any app, serves the phone remote, answers apps that ask for speech and opens Lumbina on
 * power-on. Runs in a process of its own, apart from the UI (see the manifest). Only routes the
 * service's lifecycle, keys and events to the [RemoteFeatures] built on connecting.
 */
@AndroidEntryPoint
class LumbinaAccessibilityService : AccessibilityService() {

    @Inject internal lateinit var featuresFactory: RemoteFeatures.Factory

    @Inject lateinit var log: LumbinaLog

    private val scope = MainScope()
    private var features: RemoteFeatures? = null

    override fun onServiceConnected() {
        (features ?: featuresFactory.create(this, scope).also { features = it }).start()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = features?.onKey(event) ?: false

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        features?.onAccessibilityEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        log.log(TAG, "destroyed")
        features?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "a11y"
    }
}
