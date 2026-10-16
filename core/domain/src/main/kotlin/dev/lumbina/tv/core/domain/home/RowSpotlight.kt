package dev.lumbina.tv.core.domain.home

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The row of the home screen the settings are working on now, by its key ([HomeSection.FAVORITES]
 * or a [HomeRow.key]); null when none. The settings say which, home brings it into view: the two
 * are separate screens that share nothing else but the saved look.
 */
@Singleton
class RowSpotlight @Inject constructor() {
    private val row = MutableStateFlow<String?>(null)

    val key: StateFlow<String?> = row.asStateFlow()

    fun show(key: String?) {
        row.value = key
    }
}
