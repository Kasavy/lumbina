package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.backdrop.BannerToWallpaper
import dev.lumbina.tv.core.domain.backdrop.DeleteMedia
import dev.lumbina.tv.core.domain.backdrop.DeleteUnusedMedia
import dev.lumbina.tv.core.domain.backdrop.MakeBanner
import dev.lumbina.tv.core.domain.backdrop.MediaKind
import dev.lumbina.tv.core.domain.backdrop.MediaMarks
import dev.lumbina.tv.core.domain.backdrop.ShowOnWallpaper
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettings
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.testing.FakeMediaLibrary
import dev.lumbina.tv.core.testing.FakeMediaLibrary.Companion.banner
import dev.lumbina.tv.core.testing.FakeMediaLibrary.Companion.media
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaLibraryViewModelTest {

    private val library = FakeMediaLibrary().apply {
        files = listOf(media("1.jpg"), media("2.mp4", MediaKind.Video))
    }
    private val marks = FakeSettings(MediaMarks(unplayable = setOf("2.mp4")))
    private val wallpaper = FakeSettings(WallpaperSettings())
    private val appearance = FakeSettings(HomeAppearance())

    private fun viewModel() = MediaLibraryViewModel(
        library = library,
        marks = marks,
        wallpaper = wallpaper,
        deleteMedia = DeleteMedia(library, marks, appearance, wallpaper),
        deleteUnusedMedia = DeleteUnusedMedia(library, marks, appearance),
        showOnWallpaper = ShowOnWallpaper(marks, wallpaper),
        makeBanner = MakeBanner(library, marks, wallpaper),
        bannerToWallpaper = BannerToWallpaper(library, marks, wallpaper, appearance),
        appearance = appearance,
        apps = InstalledApps { flowOf(emptyList()) },
    )

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `a video that won't play is on neither, counted unused as the clean-up would take it`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val state = viewModel.state.filterNotNull().first()
        val video = state.files.single { it.file.id == "2.mp4" }
        assertFalse(video.onWallpaper || video.onScreensaver)
        assertEquals(DeleteUnusedMedia(library, marks, appearance).unused().size, state.unused)
    }

    @Test
    fun `putting it on once gives it another try`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val video = viewModel.state.filterNotNull().first().files.single { it.file.id == "2.mp4" }
        viewModel.mark(video, MediaMark.Wallpaper, on = !video.has(MediaMark.Wallpaper))
        assertEquals(emptySet<String>(), marks.current.value.unplayable)
    }

    @Test
    fun `a banner on Lumbina's own tiles is counted as shown, under those tiles, not as an app`() = runTest {
        library.banners = listOf(banner("b1.jpg"), banner("b2.jpg"), banner("b3.jpg"))
        appearance.update {
            it.copy(
                customBanners = mapOf(
                    "all-apps" to "file:///banners/b1.jpg",
                    "input:hdmi1" to "file:///banners/b2.jpg",
                    "tv.gone/tv.gone.Main" to "file:///banners/b2.jpg",
                ),
            )
        }
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val banners = viewModel.state.filterNotNull().first().banners.associateBy { it.file.id }
        val onAllApps = banners.getValue("b1.jpg")
        assertEquals(emptyList<String>(), onAllApps.shownOn)
        assertEquals(listOf("all-apps"), onAllApps.onOwnTiles)
        val shared = banners.getValue("b2.jpg")
        assertEquals(listOf("tv.gone"), shared.shownOn)
        assertEquals(listOf("input:hdmi1"), shared.onOwnTiles)
        assertFalse(onAllApps.unusedBanner || shared.unusedBanner)
        assertTrue(banners.getValue("b3.jpg").unusedBanner)
    }
}
