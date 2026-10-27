package dev.lumbina.tv.feature.screensaver

import android.os.Bundle
import android.service.dreams.DreamService
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import dev.lumbina.tv.core.designsystem.LumbinaTheme
import javax.inject.Inject

/**
 * Lumbina as the system screensaver ("daydream"): Android starts it after the device has been
 * idle and not while a video plays, and any button ends it. A Service window has no lifecycle
 * of its own, so this provides one for Compose.
 */
@AndroidEntryPoint
class LumbinaDream :
    DreamService(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    @Inject lateinit var model: ScreensaverModel

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        setContentView(
            ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@LumbinaDream)
                setViewTreeSavedStateRegistryOwner(this@LumbinaDream)
                setContent { LumbinaTheme { Screensaver(model) } }
            },
        )
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDreamingStopped() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}

/** The screensaver on demand, from settings; any button closes it. */
@AndroidEntryPoint
class ScreensaverActivity : ComponentActivity() {

    @Inject lateinit var model: ScreensaverModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LumbinaTheme { Screensaver(model) } }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }

    // In a task of its own, it would otherwise stay behind what the service opened over it (Home, a
    // button's app, a voice command take the key before it) and come back later.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }
}
