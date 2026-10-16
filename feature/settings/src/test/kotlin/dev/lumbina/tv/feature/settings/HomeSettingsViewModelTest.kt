package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.channels.ChannelFeed
import dev.lumbina.tv.core.domain.channels.ChannelSettings
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.channels.TvChannel
import dev.lumbina.tv.core.domain.channels.TvChannels
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.home.HomeRow
import dev.lumbina.tv.core.domain.home.RowSpotlight
import dev.lumbina.tv.core.domain.home.TileSize
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.testing.FakeSettings
import dev.lumbina.tv.core.ui.UNDO_MILLIS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSettingsViewModelTest {

    private class Layouts(initial: HomeLayout) : HomeLayoutRepository {
        override val layout = MutableStateFlow(initial)

        override suspend fun update(transform: (HomeLayout) -> HomeLayout) = layout.update(transform)
    }

    private val language = object : AppLanguage {
        override val supported = false

        override fun current(): String? = null

        override fun set(tag: String?) = Unit
    }
    private val before = HomeLayout().addCategory("films", "Films").addCategory("kids", "Kids")
    private val layouts = Layouts(before)
    private val viewModel =
        HomeSettingsViewModel(
            { flowOf(emptyList()) },
            layouts,
            FakeSettings(HomeAppearance()),
            language,
            RowSpotlight(),
            object : TvChannels {
                override fun provider(): String? = null

                override fun channels() = flowOf(emptyList<ChannelFeed>())

                override fun watchNext() = flowOf(emptyList<ProgramCard>())
            },
            FakeSettings(ChannelSettings()),
            { flowOf(emptyMap()) },
        )

    private fun channelsOf(feeds: List<ChannelFeed>) = object : TvChannels {
        override fun provider(): String? = null

        override fun channels() = flowOf(feeds)

        override fun watchNext() = flowOf(emptyList<ProgramCard>())
    }

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private suspend fun offered(): String? = viewModel.state.first { it != null }!!.removed?.section?.id

    /** The category is gone and its offer is up (or, with null, down). */
    private suspend fun awaitOffer(id: String?) {
        viewModel.state.first { it != null && it.removed?.section?.id == id }
    }

    private suspend fun remove(id: String) {
        viewModel.state.first { it != null }
        viewModel.removeCategory(id)
        awaitOffer(id)
    }

    @Test
    fun `a removed category is offered back, and comes back where it stood`() = runTest {
        remove("films")
        assertEquals(listOf("kids"), layouts.layout.value.categories.map { it.id })

        viewModel.undoRemove()
        awaitOffer(null)

        assertEquals(before, layouts.layout.value)
    }

    @Test
    fun `the offer goes after a while, and then there is nothing to put back`() = runTest {
        remove("films")
        viewModel.offerFocused(false)

        advanceTimeBy(UNDO_MILLIS + 1)
        awaitOffer(null)
        viewModel.undoRemove()

        assertEquals(listOf("kids"), layouts.layout.value.categories.map { it.id })
    }

    @Test
    fun `the offer stays while it has the focus, and a while after`() = runTest {
        remove("films")
        viewModel.offerFocused(true)
        advanceTimeBy(UNDO_MILLIS * 3)
        assertEquals("films", offered())

        viewModel.offerFocused(false)
        advanceTimeBy(UNDO_MILLIS - 1)
        assertEquals("films", offered())
        advanceTimeBy(2)
        awaitOffer(null)
    }

    @Test
    fun `a second removal is the one offered back`() = runTest {
        remove("films")
        remove("kids")

        viewModel.undoRemove()
        awaitOffer(null)

        assertEquals(listOf("kids"), layouts.layout.value.categories.map { it.id })
    }

    @Test
    fun `quick moves of a channel each move it a place`() = runTest {
        val apps = listOf("a", "b", "c").map { App(AppId(it, "$it.Main"), it, isTvApp = true) }
        val feeds = apps.map { ChannelFeed(TvChannel(0, it.id.packageName, "ch", null, null), emptyList()) }
        val look = FakeSettings(HomeAppearance())
        val channels = HomeSettingsViewModel(
            { flowOf(apps) },
            Layouts(HomeLayout()),
            look,
            language,
            RowSpotlight(),
            channelsOf(feeds),
            FakeSettings(ChannelSettings()),
            { flowOf(emptyMap()) },
        )
        val shown = channels.state.first { it?.channels?.size == 3 }!!.channels.map { it.key }

        channels.moveChannel(shown.last(), -1)
        channels.moveChannel(shown.last(), -1)

        assertEquals(listOf(shown.last()) + shown.dropLast(1), look.settings.first().channelOrder)
    }

    @Test
    fun `quick presses on a row's size and switches each count`() = runTest {
        val look = FakeSettings(HomeAppearance())
        val rows = HomeSettingsViewModel(
            { flowOf(emptyList()) },
            Layouts(HomeLayout()),
            look,
            language,
            RowSpotlight(),
            channelsOf(emptyList()),
            FakeSettings(ChannelSettings()),
            { flowOf(emptyMap()) },
        )
        val key = HomeRow.Recent.key
        val sizes = TileSize.entries.sortedByDescending { it.perRow }
        val start = sizes.indexOf(look.settings.first().tileSizeOf(key))

        rows.stepRowTileSize(key, 1, sizes)
        rows.stepRowTileSize(key, 1, sizes)
        rows.toggleRowTitle(key)
        rows.toggleRowTitle(key)
        rows.toggleRowPlate(key)

        val now = look.settings.first()
        assertEquals(sizes[start + 2], now.tileSizeOf(key))
        assertEquals(true, now.showTitleIn(key))
        assertEquals(true, now.plateIn(key))
    }
}
