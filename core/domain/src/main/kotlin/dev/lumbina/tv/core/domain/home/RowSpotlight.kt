package dev.lumbina.tv.core.domain.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The part of the home screen the settings are working on now: a row by its key
 * ([HomeSection.FAVORITES] or a [HomeRow.key]) or [TOP_BAR]; null when none. The settings say which,
 * home brings it into view: the two are separate screens that share nothing else but the saved look.
 */
@Singleton
class RowSpotlight @Inject constructor() {
    private val state = MutableStateFlow<String?>(null)

    val lit: StateFlow<String?> = state.asStateFlow()

    fun show(key: String?) {
        state.value = key
    }

    companion object {
        /** The top bar, as home's grid keys it. */
        const val TOP_BAR = "header"
    }
}
