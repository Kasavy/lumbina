package dev.lumbina.tv

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.common.currentProcessName
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.health.HealAccessibilityService
import dev.lumbina.tv.core.domain.health.HealResult
import dev.lumbina.tv.core.ui.applyOwnLanguage
import dev.lumbina.tv.core.ui.lumbinaImageLoader
import dev.lumbina.tv.platform.accessibility.SpeechForApps
import dev.lumbina.tv.platform.system.HealJob
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class LumbinaApplication :
    Application(),
    SingletonImageLoader.Factory {

    @Inject lateinit var heal: HealAccessibilityService

    @Inject lateinit var log: LumbinaLog

    @Inject lateinit var profile: DeviceProfile

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var healing: Job? = null

    override fun onCreate() {
        super.onCreate()
        log.log("app", "process started: ${currentProcessName() ?: "?"}")
        followOwnLanguage()
        HealJob.ensureScheduled(this)
        HealJob.checkSoon(this)
        SpeechForApps.sync(this, wanted = profile.speechForApps)
    }

    /**
     * Brings the accessibility service back if it is down. Called when the UI opens: foreground
     * starts are allowed even while background ones are blocked after boot. Runs in the app's
     * scope so leaving the screen can't interrupt the off/on toggle halfway; one run at a time.
     */
    fun healOnUiOpen() {
        if (healing?.isActive == true) return
        healing = scope.launch {
            val result = runCatching { heal() }.getOrElse { "failed: $it" }
            if (result != HealResult.AlreadyRunning) log.log("heal", "on UI open: $result")
        }
    }

    /**
     * Before Android 13, Lumbina's own language on what the process makes outside its screens
     * (notices, the default locale for dates and sorting), now and when it is chosen again.
     */
    private fun followOwnLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
        applyOwnLanguage()
        val changed = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = applyOwnLanguage()
        }
        val filter = IntentFilter(LumbinaActions.LANGUAGE_CHANGED)
        ContextCompat.registerReceiver(this, changed, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * The system rebuilds resources from its own configuration on a change (a remote, a screen
     * mode): Lumbina's own language is put on them again.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOwnLanguage()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = lumbinaImageLoader(context)
}
