package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.lumbina.tv.core.common.LumbinaActions
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

    /** One answer per press, however many times the system hands it over. */
    private val echo = KeyEcho(
        onEcho = { log.log(TAG, "key ${it.keyCode} came twice: the service may be bound twice") },
    )

    /**
     * Lumbina's own language chosen meanwhile (before Android 13): put on the service's resources,
     * where the overlays read their words as they show (the voice's as it speaks).
     */
    private val languageChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            applyOwnLanguage()
            log.log(TAG, "language changed")
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.inOwnLanguage())
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val filter = IntentFilter(LumbinaActions.LANGUAGE_CHANGED)
            ContextCompat.registerReceiver(this, languageChanged, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    /** The system rebuilds resources on a change (a remote, a screen mode) with the language the service started in. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOwnLanguage()
    }

    override fun onServiceConnected() {
        // Keys let go for a held volume button stay so after the process ends, unless taken back here.
        filterKeys(true)
        (features ?: featuresFactory.create(this, scope).also { features = it }).start()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean =
        echo.answer(KeyStamp.of(event)) { features?.onKey(event) ?: false }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        features?.onAccessibilityEvent(event)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        log.log(TAG, "destroyed")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) unregisterReceiver(languageChanged)
        features?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "a11y"
    }
}
