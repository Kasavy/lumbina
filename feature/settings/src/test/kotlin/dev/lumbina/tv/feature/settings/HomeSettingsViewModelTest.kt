package dev.lumbina.tv.feature.settings

import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeLayout
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.locale.AppLanguage
import dev.lumbina.tv.core.testing.FakeSettings
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
        HomeSettingsViewModel({ flowOf(emptyList()) }, layouts, FakeSettings(HomeAppearance()), language)

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

        advanceTimeBy(HomeSettingsViewModel.UNDO_MILLIS + 1)
        awaitOffer(null)
        viewModel.undoRemove()

        assertEquals(listOf("kids"), layouts.layout.value.categories.map { it.id })
    }

    @Test
    fun `the offer stays while it has the focus, and a while after`() = runTest {
        remove("films")
        viewModel.offerFocused(true)
        advanceTimeBy(HomeSettingsViewModel.UNDO_MILLIS * 3)
        assertEquals("films", offered())

        viewModel.offerFocused(false)
        advanceTimeBy(HomeSettingsViewModel.UNDO_MILLIS - 1)
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
}
