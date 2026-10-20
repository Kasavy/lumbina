package dev.lumbina.tv.platform.packages

import androidx.lifecycle.Lifecycle
import dev.lumbina.tv.core.common.LumbinaLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SystemInstallerTest {

    private val context = RuntimeEnvironment.getApplication()
    private val installer = SystemInstaller(context, LumbinaLog { _, _ -> })
    private val lumbina = MutableStateFlow(Lifecycle.State.CREATED)

    private fun allow(allowed: Boolean) = shadowOf(context.packageManager).setCanRequestPackageInstalls(allowed)

    @Test
    fun `the install waits for Lumbina to be on screen, even with nothing to ask`() = runTest {
        var ready = false
        launch { installer.awaitInstallable(asked = false, lumbina).also { ready = true } }
        runCurrent()
        assertFalse(ready)
        lumbina.value = Lifecycle.State.STARTED
        runCurrent()
        assertTrue(ready)
    }

    @Test
    fun `allowed behind the settings, the install waits until Lumbina is back`() = runTest {
        allow(false)
        lumbina.value = Lifecycle.State.STARTED
        var ready = false
        launch { installer.awaitInstallable(asked = true, lumbina).also { ready = true } }
        runCurrent()
        assertFalse(ready)

        // The user goes to the settings, allows it there, and comes back.
        lumbina.value = Lifecycle.State.CREATED
        runCurrent()
        allow(true)
        runCurrent()
        assertFalse(ready)
        lumbina.value = Lifecycle.State.STARTED
        runCurrent()
        assertTrue(ready)
    }

    @Test
    fun `back without allowing it, the install goes on waiting`() = runTest {
        allow(false)
        lumbina.value = Lifecycle.State.STARTED
        var ready = false
        launch { installer.awaitInstallable(asked = true, lumbina).also { ready = true } }
        // Each state seen in turn: away to the device's screen, then back.
        runCurrent()
        lumbina.value = Lifecycle.State.CREATED
        runCurrent()
        lumbina.value = Lifecycle.State.STARTED
        runCurrent()
        assertFalse(ready)
        coroutineContext.cancelChildren()
    }
}
