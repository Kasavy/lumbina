package dev.lumbina.tv

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import dagger.hilt.android.AndroidEntryPoint
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.designsystem.LumbinaTheme
import dev.lumbina.tv.core.domain.access.PermissionAnswer
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.home.Accent
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.TextScale
import dev.lumbina.tv.core.domain.input.InputSettings
import dev.lumbina.tv.core.domain.input.InputSettingsRepository
import dev.lumbina.tv.core.ui.LocalUseLumbinaKeyboard
import dev.lumbina.tv.core.ui.LocalVoiceInput
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var inputSettings: InputSettingsRepository

    @Inject lateinit var profile: DeviceProfile

    @Inject lateinit var appearance: HomeAppearanceRepository

    @Inject lateinit var log: LumbinaLog

    @Inject lateinit var listings: TvListingsPermission

    /**
     * Other apps' channels and "continue watching": asked until the user answers (see
     * [PermissionAnswer]), then left to the channels settings; at most once per process.
     */
    private val listingsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val ask = listingsAsk ?: return@registerForActivityResult
        listingsAsk = null
        val answer = PermissionAnswer.of(
            granted = granted,
            rationaleBefore = ask.rationale,
            rationaleAfter = shouldShowRequestPermissionRationale(ask.permission),
            millis = SystemClock.elapsedRealtime() - ask.at,
        )
        log.log("channels", "TV listings permission: $answer")
        if (answer.answered) {
            getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit { putBoolean(LISTINGS_ANSWERED, true) }
        }
        if (granted) listings.onGranted()
    }

    /** The request in flight: what was asked, the rationale before it, and when. */
    private class ListingsAsk(val permission: String, val rationale: Boolean, val at: Long)

    private var listingsAsk: ListingsAsk? = null

    private val quietSemantics by lazy { QuietSemantics(this, log) }

    /** Where to go as Lumbina comes back to the screen; see [HomeReturns]. */
    private val navRequests = MutableStateFlow<NavRequest?>(null)

    private val returns = HomeReturns()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = intent.getStringExtra(LumbinaActions.EXTRA_SETTINGS_PAGE)
        returns.created(savedInstanceState != null, freshProcess = !createdInThisProcess, page)?.let(::navigate)
        createdInThisProcess = true
        setContent {
            val input by inputSettings.settings.collectAsStateWithLifecycle(InputSettings())
            val look by themeLook.collectAsStateWithLifecycle(ThemeLook())
            LumbinaTheme(accent = look.accent, textScale = look.textScale) {
                CompositionLocalProvider(
                    LocalUseLumbinaKeyboard provides input.useLumbinaKeyboard,
                    LocalVoiceInput provides voiceInput,
                ) {
                    // Surface provides the default content color; the wallpaper is the background.
                    Surface(
                        modifier = Modifier.fillMaxSize(),
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

    /** Only what the theme needs, so other appearance changes don't recompose the whole UI. */
    private val themeLook by lazy {
        appearance.settings.map { ThemeLook(it.accent, it.textScale) }.distinctUntilChanged()
    }

    override fun onStart() {
        super.onStart()
        (application as? LumbinaApplication)?.healOnUiOpen()
        returns.started(SystemClock.elapsedRealtime())?.let(::navigate)
    }

    override fun onResume() {
        super.onResume()
        returns.resumed()
        quietSemantics.start(window.decorView)
        askListingsOnce()
    }

    /** The cheap checks first: this runs on every return to the home screen. */
    private fun askListingsOnce() {
        if (listingsAskedInProcess) return
        if (getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).getBoolean(LISTINGS_ANSWERED, false)) return
        listingsAskedInProcess = true
        val permission = listings.missing() ?: return
        val rationale = shouldShowRequestPermissionRationale(permission)
        listingsAsk = ListingsAsk(permission, rationale, SystemClock.elapsedRealtime())
        listingsPermission.launch(permission)
    }

    override fun onPause() {
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

        /** Whether this process has asked for the TV listings permission (see [listingsPermission]). */
        var listingsAskedInProcess = false
    }
}

private data class ThemeLook(val accent: Accent = Accent.Amber, val textScale: TextScale = TextScale.Normal)

private const val PERMISSION_PREFS = "permissions_asked"
private const val LISTINGS_ANSWERED = "tv_listings_answered"
