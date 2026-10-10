package dev.lumbina.tv.core.domain.remote

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtherHomeTest {

    private val launcher = HomeScreen(AppId("com.acme.launcher", "Main"), "Acme launcher")
    private val simple = HomeScreen(AppId("com.acme.simple", "Home"), "Simple")

    /** The apps as they are reported to change; what they are does not matter here. */
    private val apps = MutableSharedFlow<List<App>>(replay = 1).apply { tryEmit(emptyList()) }
    private var installed = listOf(launcher, simple)
    private val other = OtherHome({ installed }, FakeSettings(RemoteSettings())) { apps }

    @Test
    fun `the first home screen is in use until another is chosen`() = runTest {
        assertEquals(OtherHomeChoice(installed, launcher), other.choice.first())
        other.use(simple)
        assertEquals(simple, other.choice.first().chosen)
    }

    @Test
    fun `one watching sees the chosen screen go with its app, and the first take its place`() = runTest {
        val seen = mutableListOf<HomeScreen?>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) { other.choice.collect { seen += it.chosen } }
        other.use(simple)
        installed = listOf(launcher)
        apps.tryEmit(emptyList())
        watching.cancel()

        assertEquals(listOf(launcher, simple, launcher), seen)
    }

    @Test
    fun `a device with no other home screen has none to lead to`() = runTest {
        installed = emptyList()
        val choice = other.choice.first()
        assertNull(choice.chosen)
        assertTrue(choice.screens.isEmpty())
    }
}
