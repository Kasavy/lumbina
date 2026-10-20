package dev.lumbina.tv.feature.screensaver

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.BackdropPhoto
import dev.lumbina.tv.core.domain.backdrop.BackdropPhotos
import dev.lumbina.tv.core.domain.backdrop.MarkUnplayable
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.domain.backdrop.MediaMarks
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettings
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.weather.CurrentWeather
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.PlaceLocator
import dev.lumbina.tv.core.domain.weather.WeatherSettings
import dev.lumbina.tv.core.testing.FakeMediaLibrary
import dev.lumbina.tv.core.testing.FakeMediaLibrary.Companion.media
import dev.lumbina.tv.core.testing.FakeSettings
import dev.lumbina.tv.core.ui.VideoFailure
import java.io.IOException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreensaverModelTest {

    private val asked = mutableListOf<Boolean>()
    private var given = ArrayDeque<BackdropPhoto?>()
    private val library = FakeMediaLibrary().apply { files = listOf(media("clip.mp4", MediaKind.Video)) }
    private val marks = FakeSettings(MediaMarks())
    private val sleep = FakeSettings(SleepSettings(screensaverChangeMinutes = 3))

    private val model = ScreensaverModel(
        weather = CurrentWeather(
            FakeSettings(WeatherSettings()),
            object : PlaceLocator {
                override suspend fun byIp(language: String): Place? = null

                override suspend fun search(query: String) = emptyList<Place>()
            },
            { throw IOException("offline") },
            refreshMillis = MINUTE,
            retryMillis = MINUTE,
        ),
        weatherSettings = FakeSettings(WeatherSettings()),
        appearance = FakeSettings(HomeAppearance()),
        backdrop = BackdropPhotos { _, _, videos ->
            asked += videos
            given.removeFirstOrNull()
        },
        sleep = sleep,
        phoneRemote = FakeSettings(PhoneRemoteSettings()),
        address = { null },
        markUnplayable = MarkUnplayable(library, marks, FakeSettings(WallpaperSettings())),
        log = LumbinaLog { _, _ -> },
    )

    private fun TestScope.collect(): List<ShownPhoto?> {
        val shown = mutableListOf<ShownPhoto?>()
        backgroundScope.launch { model.photos.collect { shown += it } }
        runCurrent()
        return shown
    }

    @Test
    fun `a video stays until it has played through, an end said before it not counting`() = runTest {
        model.videoEnded()
        given += listOf(video("a"), video("b"))
        val shown = collect()
        advanceTimeBy(5 * MINUTE)
        assertEquals(listOf("a"), shown.map { it?.video })
        model.videoEnded()
        runCurrent()
        assertEquals(listOf("a", "b"), shown.map { it?.video })
    }

    @Test
    fun `a video given no end gives way after ten minutes`() = runTest {
        given += listOf(video("a"), video("b"))
        val shown = collect()
        advanceTimeBy(10 * MINUTE + 1)
        assertEquals(listOf("a", "b"), shown.map { it?.video })
    }

    @Test
    fun `after a video fails, only photos are asked for, one of the user's own that won't play is marked`() = runTest {
        given += listOf(video(library.files.single().uri), video("b"))
        collect()
        model.videoFailed(library.files.single().uri, VideoFailure("DECODING_FORMAT_UNSUPPORTED", lasting = true))
        runCurrent()
        assertEquals(listOf(true, false), asked.take(2))
        assertEquals(setOf("clip.mp4"), marks.current.value.unplayable)
    }

    @Test
    fun `a passing failure marks nothing`() = runTest {
        given += listOf(video(library.files.single().uri))
        collect()
        model.videoFailed(library.files.single().uri, VideoFailure("IO_NETWORK_CONNECTION_FAILED", lasting = false))
        runCurrent()
        assertEquals(emptySet<String>(), marks.current.value.unplayable)
    }

    @Test
    fun `nothing to show, the gradient, asked again as often as the settings say`() = runTest {
        val shown = collect()
        advanceTimeBy(3 * MINUTE - 1)
        assertEquals(1, shown.size)
        advanceTimeBy(2)
        assertEquals(listOf(null, null), shown)
    }

    private fun video(uri: String) = BackdropPhoto.Video(uri)

    private companion object {
        const val MINUTE = 60_000L
    }
}
