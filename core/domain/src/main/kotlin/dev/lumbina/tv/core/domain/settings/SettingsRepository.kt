package dev.lumbina.tv.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** A persisted settings value of type [T]: observed as a flow, changed atomically by [update]. */
interface SettingsRepository<T> {
    val settings: Flow<T>

    suspend fun update(transform: (T) -> T)
}
