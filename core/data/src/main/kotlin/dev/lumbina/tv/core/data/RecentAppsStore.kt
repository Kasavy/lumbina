package dev.lumbina.tv.core.data

import androidx.datastore.core.DataStore
import dev.lumbina.tv.core.domain.app.RecentAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredRecentApps(val packages: List<String> = emptyList())

internal class DataStoreRecentApps(private val store: DataStore<StoredRecentApps>) : RecentAppsRepository {

    override val recent: Flow<List<String>> = store.data.map { it.packages }

    override suspend fun save(packages: List<String>) {
        store.updateData { StoredRecentApps(packages) }
    }
}
