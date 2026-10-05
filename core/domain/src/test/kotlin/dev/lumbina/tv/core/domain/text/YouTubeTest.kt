package dev.lumbina.tv.core.domain.text

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class YouTubeTest {

    private val official = YouTubeApp("com.google.android.youtube.tv", "YouTube")
    private val client = YouTubeApp("org.client", "Tube")
    private val settings = FakeSettings(YouTubeSettings())

    private fun app(packageName: String, label: String) = App(AppId(packageName, "Main"), label, isTvApp = true)

    @Test
    fun `the first app is in use until another is chosen, and again once the chosen one is gone`() = runTest {
        var installed = listOf(official, client)
        val youTube = YouTube({ installed }, settings)
        assertEquals(official, youTube.app())
        youTube.use(client)
        assertEquals(client, youTube.app())
        assertEquals(YouTubeChoice(installed, client), youTube.choice.first())
        installed = listOf(official)
        assertEquals(official, youTube.app())
    }

    @Test
    fun `the choice can be taken back, and there is none with no app`() = runTest {
        val youTube = YouTube({ listOf(official, client) }, settings)
        youTube.use(client)
        youTube.use(official)
        assertEquals(official, youTube.app())
        assertNull(YouTube({ emptyList() }, settings).app())
    }

    @Test
    fun `the app in use answers to YouTube, and another app of that name gives it up`() {
        val spoken =
            listOf(app(official.packageName, "YouTube"), app(client.packageName, "Tube"), app("films", "Films"))
        val named = spoken.withYouTube(spoken[1])
        assertEquals(listOf("Tube", "Films", "YouTube"), named.map { it.label })
        assertEquals(client.packageName, named.last().id.packageName)
    }

    @Test
    fun `the official app in use is said once, and with no app for YouTube nothing changes`() {
        val spoken = listOf(app(official.packageName, "YouTube"), app("films", "Films"))
        assertEquals(listOf("Films", "YouTube"), spoken.withYouTube(spoken[0]).map { it.label })
        assertEquals(spoken, spoken.withYouTube(null))
    }
}
