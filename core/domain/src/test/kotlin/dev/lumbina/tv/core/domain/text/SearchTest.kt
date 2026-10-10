package dev.lumbina.tv.core.domain.text

import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchTest {

    /** [takesQuery]: a system's search may open to type in and still take no query from outside. */
    private class Device(var system: Boolean, var tube: Boolean, var takesQuery: Boolean = system) : SystemSearch {
        val asked = mutableListOf<String>()

        override suspend fun available() = system

        override fun open() = system

        override fun search(query: String): Boolean {
            asked += "system: $query"
            return takesQuery
        }

        val youTubeSearch = YouTubeSearch { query ->
            asked += "youtube: $query"
            tube
        }

        val youTube =
            YouTube({
                if (tube) listOf(YouTubeApp("org.tube", "Tube")) else emptyList()
            }, FakeSettings(YouTubeSettings()))
    }

    private fun search(device: Device) =
        Search(device, device.youTube, device.youTubeSearch, FakeSettings(SearchSettings()))

    private suspend fun Search.target() = choice.first().chosen

    @Test
    fun `the system's search is used until the user chooses, YouTube where the device has no other`() = runTest {
        val both = Device(system = true, tube = true)
        assertEquals(
            SearchChoice(listOf(SearchTarget.System, SearchTarget.YouTube), SearchTarget.System),
            search(both).choice.first(),
        )
        assertEquals(SearchTarget.YouTube, search(Device(system = false, tube = true)).target())
        assertNull(search(Device(system = false, tube = false)).target())
    }

    @Test
    fun `a search goes where the user chose, and back to what is left when that is gone`() = runTest {
        val device = Device(system = true, tube = true)
        val search = search(device)
        search.use(SearchTarget.YouTube)

        assertTrue(search.search("the matrix"))
        assertEquals(listOf("youtube: the matrix"), device.asked)

        device.tube = false
        assertEquals(SearchTarget.System, search.target())
        assertTrue(search.search("again"))
        assertEquals("system: again", device.asked.last())
    }

    @Test
    fun `a system's search that takes no query fails, the user chose it and not YouTube`() = runTest {
        val device = Device(system = true, tube = true, takesQuery = false)
        assertFalse(search(device).search("the matrix"))
        assertEquals(listOf("system: the matrix"), device.asked)
    }

    @Test
    fun `with nowhere to search nothing is asked`() = runTest {
        val device = Device(system = false, tube = false)
        assertFalse(search(device).search("anything"))
        assertTrue(device.asked.isEmpty())
    }
}
