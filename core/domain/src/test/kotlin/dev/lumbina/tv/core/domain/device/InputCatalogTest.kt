package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InputCatalogTest {

    private val hdmi = InputSource("1281", "HDMI 1")

    private class Layouts(layout: HomeLayout) : HomeLayoutRepository {
        val state = MutableStateFlow(layout)
        override val layout: Flow<HomeLayout> = state
        override suspend fun update(transform: (HomeLayout) -> HomeLayout) {
            state.value = transform(state.value)
        }
    }

    /** Lists nothing [emptyTimes] times, as a device whose inputs come up a while after Lumbina. */
    private class Sources(private var emptyTimes: Int, private val inputs: List<InputSource>) : InputSources {
        var asked = 0
        override suspend fun showAndroid() = false
        override suspend fun list(): List<InputSource> {
            asked++
            return if (emptyTimes-- > 0) emptyList() else inputs
        }
        override suspend fun current(): String? = null
        override suspend fun open(id: String) = true
        var presence = emptyMap<String, InputPresence>()
        var presenceAsked = 0
        override suspend fun presence(): Map<String, InputPresence> {
            presenceAsked++
            return presence
        }
    }

    private fun profile(sources: InputSources?) = object : DeviceProfile {
        override val id = "test"
        override val priority = 0
        override val keyMap = dev.lumbina.tv.core.domain.remote.KeyMap { _, _ -> null }
        override val stockLauncher = null
        override val inputs = sources
        override fun matches(device: DeviceFingerprint) = true
    }

    @Test
    fun `none at first, then the inputs once the device lists them, under the user's names`() = runTest {
        val sources = Sources(emptyTimes = 2, inputs = listOf(hdmi))
        val layouts = Layouts(HomeLayout().renameInput(hdmi.id, "Приставка"))
        val seen = InputCatalog(profile(sources), layouts).inputs.take(2).toList()
        assertEquals(listOf(emptyList(), listOf(hdmi.copy(name = "Приставка"))), seen)
        assertEquals(3, sources.asked)
    }

    @Test
    fun `found once, the device is not asked again`() = runTest {
        val sources = Sources(emptyTimes = 0, inputs = listOf(hdmi))
        val catalog = InputCatalog(profile(sources), Layouts(HomeLayout()))
        catalog.inputs.take(2).toList()
        assertEquals(listOf(hdmi), catalog.inputs.first())
        assertEquals(1, sources.asked)
    }

    @Test
    fun `a device without inputs has none`() = runTest {
        assertEquals(emptyList<InputSource>(), InputCatalog(profile(null), Layouts(HomeLayout())).inputs.first())
    }

    @Test
    fun `live inputs carry what is on them`() = runTest {
        val sources = Sources(emptyTimes = 0, inputs = listOf(hdmi))
        sources.presence = mapOf(hdmi.id to InputPresence.Working("MIBOX4"))
        val catalog = InputCatalog(profile(sources), Layouts(HomeLayout()))
        val working = catalog.live.first { list -> list.any { it.presence != null } }
        assertEquals(listOf(hdmi.copy(presence = InputPresence.Working("MIBOX4"))), working)
        assertEquals("HDMI 1 · MIBOX4", working.single().caption)
    }

    @Test
    fun `one poll every few seconds however many screens show it`() = runTest {
        val sources = Sources(emptyTimes = 0, inputs = listOf(hdmi))
        sources.presence = mapOf(hdmi.id to InputPresence.Connected)
        val catalog = InputCatalog(profile(sources), Layouts(HomeLayout()), backgroundScope)
        backgroundScope.launch { catalog.live.collect {} }
        backgroundScope.launch { catalog.live.collect {} }
        runCurrent()
        assertEquals(1, sources.presenceAsked)
        advanceTimeBy(5_001)
        assertEquals(2, sources.presenceAsked)
    }

    @Test
    fun `after an empty answer the device is asked again, only less often`() = runTest {
        val sources = Sources(emptyTimes = 0, inputs = listOf(hdmi))
        val catalog = InputCatalog(profile(sources), Layouts(HomeLayout()), backgroundScope)
        backgroundScope.launch { catalog.live.collect {} }
        runCurrent()
        advanceTimeBy(30_000)
        assertEquals(1, sources.presenceAsked)
        sources.presence = mapOf(hdmi.id to InputPresence.Working("MIBOX4"))
        advanceTimeBy(30_001)
        assertEquals(2, sources.presenceAsked)
        assertEquals(InputPresence.Working("MIBOX4"), catalog.live.first().single().presence)
    }

    @Test
    fun `no poll while there are no inputs`() = runTest {
        val sources = Sources(emptyTimes = 100, inputs = listOf(hdmi))
        val catalog = InputCatalog(profile(sources), Layouts(HomeLayout()), backgroundScope)
        backgroundScope.launch { catalog.live.collect {} }
        advanceTimeBy(30_000)
        assertEquals(0, sources.presenceAsked)
    }

    @Test
    fun `a screen shown again starts from the last answer, not from nothing`() = runTest {
        val sources = Sources(emptyTimes = 0, inputs = listOf(hdmi))
        sources.presence = mapOf(hdmi.id to InputPresence.Empty)
        val catalog = InputCatalog(profile(sources), Layouts(HomeLayout()), backgroundScope)
        val first = backgroundScope.launch { catalog.live.collect {} }
        runCurrent()
        first.cancel()
        // Long enough for the poll to stop.
        advanceTimeBy(10_000)
        val seen = mutableListOf<List<InputSource>>()
        backgroundScope.launch { catalog.live.collect { seen += it } }
        runCurrent()
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { list -> list.single().presence == InputPresence.Empty }, "$seen")
    }

    @Test
    fun `the device's name stays out of a name that has it, and working inputs come first, empty ones last`() {
        assertEquals("Mibox4 в спальне", inputCaption("Mibox4 в спальне", InputPresence.Working("MIBOX4")))
        assertEquals("HDMI 1", inputCaption("HDMI 1", InputPresence.Connected))
        val empty = InputSource("1", "HDMI 1", InputPresence.Empty)
        val unknown = InputSource("2", "HDMI 2")
        val working = InputSource("3", "HDMI 3", InputPresence.Working(null))
        val cabled = InputSource("4", "HDMI 4", InputPresence.Connected)
        assertEquals(listOf(working, unknown, cabled, empty), listOf(empty, unknown, working, cabled).byPresence())
    }
}
