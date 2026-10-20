package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.access.AccessSettings
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.device.DeviceFingerprint
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.diagnostics.SystemInfo
import dev.lumbina.tv.core.domain.remote.KeyMap
import dev.lumbina.tv.core.domain.update.AppUpdates
import dev.lumbina.tv.core.domain.update.Release
import dev.lumbina.tv.core.domain.update.UpdateCheck
import dev.lumbina.tv.core.domain.update.UpdateStep
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    /** Says what [found] says; each install is a channel the test sends the steps into. */
    private class Updates(var found: UpdateCheck) : AppUpdates {
        val installs = mutableListOf<Channel<UpdateStep>>()

        override val supported = true

        override suspend fun check() = found

        override fun install(release: Release): Flow<UpdateStep> =
            Channel<UpdateStep>(Channel.UNLIMITED).also { installs += it }.receiveAsFlow()

        var askedToInstall = 0

        override fun askToInstallApps() {
            askedToInstall++
        }
    }

    private val release =
        Release("0.0.4", "lumbina-v0.0.4.apk", "https://example.org/a.apk", 46, "https://example.org/s")
    private val updates = Updates(UpdateCheck.Available(release))

    private fun viewModel() = AboutViewModel(Profile, System, FakeSettings(AccessSettings()), updates)

    private val AboutViewModel.now get() = update.value

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `a found version is downloaded, and a second OK does not start it again`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        assertEquals(UpdateUi.Available(release), viewModel.now)

        viewModel.onUpdate()
        assertEquals(UpdateUi.Downloading(release, 0), viewModel.now)
        viewModel.onUpdate()
        assertEquals(1, updates.installs.size)
    }

    @Test
    fun `an install that breaks unexpectedly is a failed try, offered again`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        updates.installs.single().close(IllegalStateException("installer"))
        assertEquals(UpdateUi.Broken(release, corrupt = false), viewModel.now)
    }

    @Test
    fun `OK while the system asks stops waiting, and the version is offered again`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        updates.installs.single().send(UpdateStep.Installing)
        assertEquals(UpdateAction.StopWaiting(release), viewModel.now?.action())

        viewModel.onUpdate()
        assertEquals(UpdateUi.Available(release), viewModel.now)
    }

    @Test
    fun `waiting to be allowed to install, OK opens the screen again, during the download too`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        val steps = updates.installs.single()
        steps.send(UpdateStep.Downloading(0.5f, needsPermission = true))
        assertEquals(UpdateUi.Downloading(release, 50, needsPermission = true), viewModel.now)
        viewModel.onUpdate()
        assertEquals(1, updates.askedToInstall)
        steps.send(UpdateStep.NeedsPermission)
        assertEquals(UpdateUi.NeedsPermission(release), viewModel.now)

        viewModel.onUpdate()
        assertEquals(2, updates.askedToInstall)

        // The flow says what comes next: the row follows it.
        steps.send(UpdateStep.Installing)
        assertEquals(UpdateUi.Installing(release), viewModel.now)
        assertEquals(1, updates.installs.size)
    }

    @Test
    fun `leaving the page while waiting to be allowed stops waiting and offers the version again`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        val steps = updates.installs.single()
        steps.send(UpdateStep.NeedsPermission)

        viewModel.forgetDone()
        assertEquals(UpdateUi.Available(release), viewModel.now)
        // What the install says after it was given up changes nothing.
        steps.trySend(UpdateStep.Installing)
        assertEquals(UpdateUi.Available(release), viewModel.now)
    }

    @Test
    fun `leaving the page while downloading with the screen to allow it open stops it all`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        val steps = updates.installs.single()
        steps.send(UpdateStep.Downloading(0.2f, needsPermission = true))

        viewModel.forgetDone()
        assertEquals(UpdateUi.Available(release), viewModel.now)
        steps.trySend(UpdateStep.NeedsPermission)
        assertEquals(UpdateUi.Available(release), viewModel.now)
    }

    @Test
    fun `leaving the page during a plain download lets it go on`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        updates.installs.single().send(UpdateStep.Downloading(0.2f))

        viewModel.forgetDone()
        assertEquals(UpdateUi.Downloading(release, 20), viewModel.now)
    }

    @Test
    fun `refused, OK does nothing, and leaving the page forgets it`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        updates.installs.single().send(UpdateStep.Refused)

        viewModel.onUpdate()
        assertEquals(UpdateUi.Refused, viewModel.now)
        assertEquals(1, updates.installs.size)

        viewModel.forgetDone()
        assertEquals(UpdateUi.Idle, viewModel.now)
    }

    @Test
    fun `leaving the page keeps a failed version offered, and forgets that it was up to date`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        updates.installs.single().send(UpdateStep.Corrupt)
        viewModel.forgetDone()
        assertEquals(UpdateUi.Available(release), viewModel.now)

        updates.found = UpdateCheck.UpToDate
        val fresh = viewModel()
        fresh.onUpdate()
        assertEquals(UpdateUi.UpToDate, fresh.now)
        fresh.forgetDone()
        assertEquals(UpdateUi.Idle, fresh.now)
    }

    @Test
    fun `leaving the page while installing changes nothing`() = runTest {
        val viewModel = viewModel()
        viewModel.onUpdate()
        viewModel.onUpdate()
        updates.installs.single().send(UpdateStep.InstallingItself)
        viewModel.forgetDone()
        assertEquals(UpdateUi.InstallingItself, viewModel.now)
    }

    private object Profile : DeviceProfile {
        override val id = "acme"
        override val priority = 0
        override val keyMap = KeyMap { _, _ -> null }
        override val stockLauncher: AppId? = null

        override fun matches(device: DeviceFingerprint) = true
    }

    private object System : SystemInfo {
        override val appVersion = "0.0.3"
        override val debugBuild = false
        override val manufacturer = "Acme"
        override val model = "TV_1"
        override val androidRelease = "11"
        override val sdkInt = 30
        override val totalRamBytes = 0L
        override val availableRamBytes = 0L
        override val freeStorageBytes = 0L
        override val firmware = ""
        override val fingerprint = ""
        override val hardware = ""
        override val uptimeMillis = 0L
        override val inputDevices = emptyList<String>()
        override val homeApp: String? = null
        override val microphoneGranted = false
        override val processes = emptyList<String>()
        override val processExits = emptyList<String>()

        override fun packageVersion(packageName: String): String? = null
    }
}
