package dev.lumbina.tv.feature.screensaver

import android.content.Context
import android.os.Bundle
import android.service.dreams.DreamService
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
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
import dev.lumbina.tv.core.ui.inOwnLanguage
import javax.inject.Inject

/**
 * Lumbina as the system screensaver ("daydream"): Android starts it after the device has been
 * idle and not while a video plays, and any button ends it, a click of a mouse or a touch too. A
 * Service window has no lifecycle of its own, so this provides one for Compose.
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.inOwnLanguage())
    }

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
                setContent {
                    LumbinaTheme {
                        Screensaver(
                            model,
                            Modifier.closedByClick(stringResource(R.string.screensaver_close)) {
                                finish()
                            },
                        )
                    }
                }
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

    // A pointer only moved, or its wheel turned, leaves it on: an air mouse put down may still drift.
    // Not interactive, the dream would end at any of these; a press comes as a touch and still ends it.
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) || super.dispatchGenericMotionEvent(event)
}

/** The screensaver on demand, from settings; any button closes it, and a click of a mouse or a touch. */
@AndroidEntryPoint
class ScreensaverActivity : ComponentActivity() {

    @Inject lateinit var model: ScreensaverModel

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.inOwnLanguage())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LumbinaTheme {
                Screensaver(model, Modifier.closedByClick(stringResource(R.string.screensaver_close)) { finish() })
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        finish()
        return true
    }

    // A press of a mouse comes as a touch; its move and release stay here, so they press nothing
    // behind. A pointer only moved comes as hover, to dispatchGenericMotionEvent, and leaves it on.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) finish()
        return true
    }

    // In a task of its own, it would otherwise stay behind what the service opened over it (Home, a
    // button's app, a voice command take the key before it) and come back later.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }
}

/**
 * The whole screensaver as one thing to click, which closes it: the phone touchpad clicks through
 * the accessibility tree, and there it found nothing to click.
 */
private fun Modifier.closedByClick(label: String, close: () -> Unit): Modifier = semantics {
    onClick(label) {
        close()
        true
    }
}
