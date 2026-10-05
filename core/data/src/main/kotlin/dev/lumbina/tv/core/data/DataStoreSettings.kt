package dev.lumbina.tv.core.data

import android.util.Log
import androidx.datastore.core.DataStore
import dev.lumbina.tv.core.domain.settings.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Settings kept in [store] in their on-disk form [S], seen as [D] through [toDomain] and [toStored].
 * With [rewriteOnRead] the stored value is passed through both mappings before it is first read,
 * so fields that [toStored] fills in (e.g. a generated key) exist before anyone sees them.
 */
internal class DataStoreSettings<S, D>(
    private val store: DataStore<S>,
    private val toDomain: (S) -> D,
    private val toStored: (D) -> S,
    rewriteOnRead: Boolean = false,
) : SettingsRepository<D> {

    override val settings: Flow<D> = store.data
        .onStart { if (rewriteOnRead) update { it } }
        .map { toDomain(it) }

    /**
     * A failed write (no space left, say) keeps the old value: callers change settings from plain
     * `launch`es, where the exception would take the process down, and the screen shows what is stored.
     */
    override suspend fun update(transform: (D) -> D) {
        try {
            store.updateData { toStored(transform(toDomain(it))) }
        } catch (e: IOException) {
            Log.w(TAG, "Settings not saved", e)
        }
    }

    private companion object {
        const val TAG = "Settings"
    }
}
