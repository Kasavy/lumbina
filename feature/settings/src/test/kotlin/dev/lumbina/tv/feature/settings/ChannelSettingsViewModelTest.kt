package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.channels.ChannelFeed
import dev.lumbina.tv.core.domain.channels.ChannelSettings
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.channels.TvChannel
import dev.lumbina.tv.core.domain.channels.TvChannels
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelSettingsViewModelTest {

    private class Permission(var missing: String?) : TvListingsPermission {
        var readAgain = 0

        override fun missing(): String? = missing

        override fun onGranted() {
            readAgain++
        }
    }

    private val feeds = listOf(
        feed("org.films", "New", programs = 2),
        feed("org.films", "Top", programs = 0),
        feed("org.gone", "Left behind", programs = 1),
    )
    private val channels = object : TvChannels {
        override fun isAvailable() = true

        override fun channels(): Flow<List<ChannelFeed>> = flowOf(feeds)

        override fun watchNext(): Flow<List<ProgramCard>> = flowOf(emptyList())
    }
    private val permission = Permission(missing = null)
    private val settings = FakeSettings(ChannelSettings(hiddenChannels = setOf("org.films/Top")))
    private val apps = listOf(App(AppId("org.films", "org.films.Main"), "Films", isTvApp = true))

    private fun viewModel() = ChannelSettingsViewModel(channels, permission, { flowOf(apps) }, settings)

    private suspend fun ChannelSettingsViewModel.shown() = state.filterNotNull().first()

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `channels come with their app's name, and without those of apps that are gone`() = runTest {
        val shown = viewModel().shown()

        assertEquals(
            listOf(
                ChannelChoice("org.films/New", "New", "Films", programs = 2, shown = true),
                ChannelChoice("org.films/Top", "Top", "Films", programs = 0, shown = false),
            ),
            shown.channels,
        )
        assertNull(shown.missingPermission)
    }

    @Test
    fun `toggling hides a channel and shows it again`() = runTest {
        val viewModel = viewModel()

        viewModel.toggle("org.films/New")
        assertEquals(setOf("org.films/Top", "org.films/New"), settings.current.value.hiddenChannels)

        viewModel.toggle("org.films/Top")
        assertEquals(setOf("org.films/New"), settings.current.value.hiddenChannels)
    }

    @Test
    fun `the rows are read again once, when the missing permission is granted`() = runTest {
        permission.missing = LISTINGS
        val viewModel = viewModel()

        viewModel.recheck()
        assertEquals(LISTINGS, viewModel.shown().missingPermission)
        assertEquals(0, permission.readAgain)

        // Granted in the dialog or in the system's settings: the page only learns by asking.
        permission.missing = null
        viewModel.recheck()
        assertNull(viewModel.shown().missingPermission)
        assertEquals(1, permission.readAgain)

        viewModel.recheck()
        assertEquals(1, permission.readAgain)
    }

    private companion object {
        const val LISTINGS = "android.permission.READ_TV_LISTINGS"

        fun feed(app: String, name: String, programs: Int) = ChannelFeed(
            TvChannel(id = name.hashCode().toLong(), packageName = app, name = name, appLinkUri = null, logoUri = null),
            List(programs) { ProgramCard(it.toLong(), app, "Program $it", null, null, null, null) },
        )
    }
}
