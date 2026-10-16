package dev.lumbina.tv.feature.home

import dev.lumbina.tv.core.domain.channels.ChannelFeed
import dev.lumbina.tv.core.domain.channels.ChannelSettings
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.channels.TvChannels
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
class ChannelsViewModelTest {

    /** The provider's rows, read again whenever the permission is granted, as the real one does. */
    private val readings = MutableStateFlow(0)
    private val channels = object : TvChannels {
        override fun provider() = "org.provider"

        override fun channels(): Flow<List<ChannelFeed>> = readings.map { emptyList() }

        override fun watchNext(): Flow<List<ProgramCard>> = flowOf(emptyList())
    }

    private val permission = object : TvListingsPermission {
        var granted = false

        override fun missing(): String? = LISTINGS.takeUnless { granted }

        override fun onGranted() {
            readings.value++
        }
    }

    private fun viewModel() = ChannelsViewModel(
        channels = channels,
        settings = FakeSettings(ChannelSettings()),
        installedApps = { flowOf(emptyList()) },
        usage = { flowOf(emptyMap()) },
        launcher = { false },
        permission = permission,
    )

    @Before
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    @Test
    fun `granted in the setup over home, home stops asking once the rows are read again`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        assertEquals(LISTINGS, viewModel.state.value.missingPermission)

        // The setup's own question: home is not told, but the rows are read again.
        permission.granted = true
        permission.onGranted()

        assertNull(viewModel.state.value.missingPermission)
    }

    private companion object {
        const val LISTINGS = "android.permission.READ_TV_LISTINGS"
    }
}
