package dev.lumbina.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dagger.hilt.android.AndroidEntryPoint
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.designsystem.LumbinaTheme
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.input.InputSettings
import dev.lumbina.tv.core.domain.input.InputSettingsRepository
import dev.lumbina.tv.core.domain.theme.Themes
import dev.lumbina.tv.core.ui.LocalScreenMarks
import dev.lumbina.tv.core.ui.LocalTapWindows
import dev.lumbina.tv.core.ui.LocalUseLumbinaKeyboard
import dev.lumbina.tv.core.ui.LocalVoiceInput
import dev.lumbina.tv.core.ui.ProvideMouseClicks
import dev.lumbina.tv.core.ui.ScreenMarks
import dev.lumbina.tv.core.ui.TapWindows
import dev.lumbina.tv.core.ui.inOwnLanguage
import dev.lumbina.tv.core.ui.mouseClicks
import dev.lumbina.tv.platform.accessibility.PointerUse
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var inputSettings: InputSettingsRepository

    @Inject lateinit var profile: DeviceProfile

    @Inject lateinit var appearance: HomeAppearanceRepository

    @Inject lateinit var log: LumbinaLog

    private var backToAndroid: Job? = null

    private val quietSemantics by lazy { QuietSemantics(this, log) }

    /** Where to go as Lumbina comes back to the screen; see [HomeReturns]. */
    private val navRequests = MutableStateFlow<NavRequest?>(null)

    private val returns = HomeReturns()

    /** Lumbina's own language chosen before Android 13: the screens are made anew in it, as Android 13 does itself. */
    private val languageChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            recreate()
        }
    }

    /** Lumbina's dialogs and popups as they show, for the phone touchpad's taps. */
    private val taps = TapWindows()

    /** The phone touchpad clicked where accessibility finds nothing to click: the screen taps there. */
    private val tapAt = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val x = intent.getIntExtra(LumbinaActions.EXTRA_X, -1)
            val y = intent.getIntExtra(LumbinaActions.EXTRA_Y, -1)
            if (x >= 0 && y >= 0) taps.tapAt(x, y, window.decorView)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.inOwnLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val filter = IntentFilter(LumbinaActions.LANGUAGE_CHANGED)
            ContextCompat.registerReceiver(this, languageChanged, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        ContextCompat.registerReceiver(
            this,
            tapAt,
            IntentFilter(LumbinaActions.TAP_AT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val page = intent.getStringExtra(LumbinaActions.EXTRA_SETTINGS_PAGE)
        returns.created(savedInstanceState != null, freshProcess = !createdInThisProcess, page)?.let(::navigate)
        createdInThisProcess = true
        setContent {
            val input by inputSettings.settings.collectAsStateWithLifecycle(InputSettings())
            val look by themeLook.collectAsStateWithLifecycle(ThemeLook())
            // "As the system" follows the device's dark or light setting, live.
            val theme = Themes.forSetting(look.theme, systemDark = isSystemInDarkTheme())
            LumbinaTheme(accent = look.accent, textScale = look.textScale, theme = theme) {
                CompositionLocalProvider(
                    LocalUseLumbinaKeyboard provides input.useLumbinaKeyboard,
                    LocalVoiceInput provides voiceInput,
                    LocalScreenMarks provides remember { ScreenMarks() },
                    LocalTapWindows provides taps,
                ) {
                    ProvideMouseClicks(onPointer = pointer::used) {
                        // Surface provides the default content color; the wallpaper is the background.
                        Surface(
                            // A mouse or an air mouse clicks too; dialogs and popups have this of their own.
                            modifier = Modifier.fillMaxSize().mouseClicks(),
                            colors = SurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            val request by navRequests.collectAsStateWithLifecycle()
                            LumbinaNavHost(request)
                        }
                    }
                }
            }
        }
    }

    /** Only what the theme needs, so other appearance changes don't recompose the whole UI. */
    private val themeLook by lazy {
        appearance.settings.map { ThemeLook(it.accent.takeIf { _ -> it.accentChosen }, it.textScale, it.theme) }
            .distinctUntilChanged()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) unregisterReceiver(languageChanged)
        unregisterReceiver(tapAt)
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        (application as? LumbinaApplication)?.healOnUiOpen()
        returns.started(SystemClock.elapsedRealtime())?.let(::navigate)
    }

    /** A mouse in use here is told to the service, so its screensaver and auto-sleep wait. */
    private val pointer by lazy { PointerUse(this) }

    // A mouse click comes as a touch; its moves and wheel as generic motion. The remote's keys the
    // service hears itself.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        pointer.used()
        return super.dispatchTouchEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) pointer.used()
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onResume() {
        super.onResume()
        returns.resumed()
        quietSemantics.start(window.decorView)
        // Lumbina comes back on Android's own picture; not once it is left again (an input opened meanwhile).
        backToAndroid = lifecycleScope.launch {
            if (profile.inputs?.showAndroid() == true) log.log(TAG, "another input was chosen: back to Android's")
        }
    }

    override fun onPause() {
        backToAndroid?.cancel()
        quietSemantics.stop()
        super.onPause()
    }

    override fun onStop() {
        returns.stopped(SystemClock.elapsedRealtime())
        super.onStop()
    }

    private fun navigate(request: NavRequest) {
        navRequests.value = request
    }

    /** The keyboard's mic key, on devices whose remote mic Lumbina can use. */
    private val voiceInput: (() -> Unit)? by lazy {
        if (!profile.hasRemoteMic) {
            null
        } else {
            { sendBroadcast(Intent(LumbinaActions.TOGGLE_VOICE).setPackage(packageName)) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val page = intent.getStringExtra(LumbinaActions.EXTRA_SETTINGS_PAGE)
        navigate(returns.newIntent(page, toStartAsked = intent.getBooleanExtra(LumbinaActions.EXTRA_TO_START, false)))
    }

    private companion object {
        /** Whether this process has created the activity before; not, when state comes from a killed one. */
        var createdInThisProcess = false

        const val TAG = "MainActivity"
    }
}

private data class ThemeLook(
    val accent: Accent? = null,
    val textScale: TextScale = TextScale.Normal,
    val theme: String = HomeAppearance.THEME_DARK,
)
