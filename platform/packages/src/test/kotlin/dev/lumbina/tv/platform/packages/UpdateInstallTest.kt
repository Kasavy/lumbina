package dev.lumbina.tv.platform.packages

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.update.Release
import dev.lumbina.tv.core.domain.update.ShellInstall
import dev.lumbina.tv.core.domain.update.UpdateStep
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateInstallTest {

    @get:Rule val folder = TemporaryFolder()

    private val release =
        Release("0.0.6", "lumbina-v0.0.6.apk", "https://example.org/a.apk", 4, "https://example.org/s")

    /** A device where each step does what the test sets. */
    private class Device : InstallSteps {
        var adb = false
        var allowed = false
        var hasScreen = true
        var fetched: () -> Boolean = { true }
        var unfit: UpdateStep? = null
        var overAdb = ShellInstall.Done
        var system: UpdateStep = UpdateStep.NotInstalled
        val asked = mutableListOf<String>()

        override suspend fun overAdb() = adb

        override fun mayInstallApps() = allowed

        override fun canAskToInstallApps() = hasScreen

        override fun askToInstallApps(): Boolean {
            asked += "screen"
            return hasScreen
        }

        override suspend fun fetch(release: Release, file: File, progress: suspend (Float) -> Unit): Boolean {
            file.writeText("apk")
            progress(1f)
            return fetched()
        }

        override fun unfit(file: File) = unfit

        override suspend fun installOverAdb(file: File) = overAdb

        override fun stepAfter(answer: ShellInstall) = if (answer == ShellInstall.Refused) UpdateStep.Refused else null

        override suspend fun awaitInstallable(asked: Boolean) {
            this.asked += "await $asked"
        }

        override suspend fun installThroughSystem(file: File) = system
    }

    private val device = Device()
    private val file get() = File(folder.root, "lumbina.apk")

    private suspend fun steps() = UpdateInstall(
        device,
        LumbinaLog { _, _ ->
        },
        askAfterMillis = 0,
    ).run(release, file).toList()

    @Test
    fun `not allowed yet, the screen to allow it opens with the download, and the install waits for it`() = runTest {
        val steps = steps()
        assertEquals(UpdateStep.Downloading(0f, needsPermission = true), steps[1])
        assertEquals(listOf("screen", "await true"), device.asked)
        assertEquals(
            listOf(UpdateStep.NeedsPermission, UpdateStep.Installing, UpdateStep.NotInstalled),
            steps.takeLast(3),
        )
        assertTrue(file.isFile)
    }

    @Test
    fun `without the screen to allow it, nothing says a list will open`() = runTest {
        device.hasScreen = false
        val steps = steps()
        assertTrue(steps.none { it is UpdateStep.Downloading && it.needsPermission })
        assertEquals(listOf("await false"), device.asked)
    }

    @Test
    fun `over adb, a refused package goes and nothing is asked of the system`() = runTest {
        device.adb = true
        device.overAdb = ShellInstall.Refused
        val steps = steps()
        assertEquals(listOf(UpdateStep.InstallingItself, UpdateStep.Refused), steps.takeLast(2))
        assertEquals(emptyList<String>(), device.asked)
        assertFalse(file.exists())
    }

    @Test
    fun `adb failing, the row says why the screen comes before it opens, then the system installs`() = runTest {
        device.adb = true
        device.overAdb = ShellInstall.Failed
        val steps = steps()
        val afterAdb = steps.dropWhile { it != UpdateStep.InstallingItself }
        assertEquals(UpdateStep.Downloading(1f, needsPermission = true), afterAdb[1])
        assertEquals(listOf("screen", "await true"), device.asked)
        assertEquals(UpdateStep.NotInstalled, steps.last())
    }

    @Test
    fun `a package that is not the published one goes`() = runTest {
        device.allowed = true
        device.fetched = { false }
        assertEquals(UpdateStep.Corrupt, steps().last())
        assertFalse(file.exists())
    }

    @Test
    fun `cut short by the network, the package stays for the next try, one not written does not`() = runTest {
        device.allowed = true
        device.fetched = { throw IOException("dropped") }
        assertEquals(UpdateStep.Failed, steps().last())
        assertTrue(file.exists())
        device.fetched = { throw FinalAnswer("disk full") }
        assertEquals(UpdateStep.Failed, steps().last())
        assertFalse(file.exists())
    }
}
