package dev.lumbina.tv

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.health.HealAccessibilityService
import dev.lumbina.tv.core.domain.health.HealResult
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
        val process = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) getProcessName() else "?"
        log.log("app", "process started: $process")
        HealJob.ensureScheduled(this)
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

    override fun newImageLoader(context: PlatformContext): ImageLoader = lumbinaImageLoader(context)
}
