package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
}
