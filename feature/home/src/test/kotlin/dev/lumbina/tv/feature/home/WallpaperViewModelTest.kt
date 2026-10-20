package dev.lumbina.tv.feature.home

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.CurrentWallpaper
import dev.lumbina.tv.core.domain.backdrop.MarkUnplayable
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.domain.backdrop.MediaMarks
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettings
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.weather.WeatherSettings
import dev.lumbina.tv.core.testing.FakeMediaLibrary
import dev.lumbina.tv.core.testing.FakeMediaLibrary.Companion.media
import dev.lumbina.tv.core.testing.FakeSettings
import dev.lumbina.tv.core.ui.VideoFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperViewModelTest {

    private val library = FakeMediaLibrary().apply { files = listOf(media("clip.mp4", MediaKind.Video)) }
    private val marks = FakeSettings(MediaMarks())
    private val settings = FakeSettings(WallpaperSettings())
    private val uri = library.files.single().uri

    private val viewModel = WallpaperViewModel(
        wallpaper = CurrentWallpaper(
            settings,
            library,
            marks,
            { emptyList() },
            object : WallpaperPlugins {
                override fun installed() = emptyList<WallpaperPluginInfo>()

                override suspend fun wallpapers(id: String) =
                    emptyList<dev.lumbina.tv.core.domain.backdrop.WallpaperItem>()

                override fun openSettings(id: String) = false
            },
        ),
        settings = settings,
        appearance = FakeSettings(HomeAppearance()),
        weather = FakeSettings(WeatherSettings()),
        markUnplayable = MarkUnplayable(library, marks, settings),
        log = LumbinaLog { _, _ -> },
    )

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `a video the device can't play is marked, and the wallpaper goes on`() = runBlocking {
        viewModel.videoFailed(uri, VideoFailure("DECODING_FORMAT_UNSUPPORTED", lasting = true))
        withTimeout(WAIT_MILLIS) { marks.current.first { it.unplayable == setOf("clip.mp4") } }
        assertEquals(setOf("clip.mp4"), marks.current.value.unplayable)
    }

    @Test
    fun `a passing failure marks nothing, the player tries again`() = runBlocking {
        viewModel.videoFailed(uri, VideoFailure("IO_NETWORK_CONNECTION_FAILED", lasting = false))
        delay(SETTLE_MILLIS)
        assertEquals(emptySet<String>(), marks.current.value.unplayable)
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val SETTLE_MILLIS = 200L
    }
}
