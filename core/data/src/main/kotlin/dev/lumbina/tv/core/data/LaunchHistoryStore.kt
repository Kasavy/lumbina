package dev.lumbina.tv.core.data

import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import dev.lumbina.tv.core.domain.app.LaunchHistory
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredLaunch(val packageName: String, val at: Long)

@Serializable
internal data class StoredLaunchHistory(val launches: List<StoredLaunch> = emptyList())

/** Launches of the last [LAUNCH_HISTORY_DAYS] days, at most [MAX_LAUNCHES] of them. */
internal class DataStoreLaunchHistory(
    private val store: DataStore<StoredLaunchHistory>,
    private val now: () -> Long = System::currentTimeMillis,
) : LaunchHistory {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun record(packageName: String) {
        scope.launch {
            try {
                add(packageName)
            } catch (e: IOException) {
                // A lost launch only nudges the ordering; not worth failing the caller over.
                Log.w(TAG, "Could not record a launch of $packageName", e)
            }
        }
    }

    /** Adds a launch now, dropping the ones too old to count. */
    @VisibleForTesting
    internal suspend fun add(packageName: String) {
        store.updateData { stored ->
            val recent = stored.launches.filter { it.at >= since() }
            StoredLaunchHistory((recent + StoredLaunch(packageName, now())).takeLast(MAX_LAUNCHES))
        }
    }

    override fun openings(): Flow<Map<String, Long>> = store.data.map { stored ->
        val since = since()
        stored.launches.filter {
            it.at >= since
        }.groupingBy { it.packageName }.eachCount().mapValues { it.value.toLong() }
    }

    private fun since() = now() - LAUNCH_HISTORY_DAYS * DAY_MILLIS

    private companion object {
        const val LAUNCH_HISTORY_DAYS = 14
        const val MAX_LAUNCHES = 500
        const val DAY_MILLIS = 24 * 60 * 60_000L
        const val TAG = "LaunchHistory"
    }
}
