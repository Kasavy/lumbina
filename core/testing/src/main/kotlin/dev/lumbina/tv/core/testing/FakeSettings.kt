package dev.lumbina.tv.core.testing

import dev.lumbina.tv.core.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** In-memory settings; tests read and set [current] directly. */
class FakeSettings<T>(initial: T) : SettingsRepository<T> {
    val current = MutableStateFlow(initial)

    override val settings: StateFlow<T> = current

    override suspend fun update(transform: (T) -> T) = current.update(transform)
}
