package dev.lumbina.tv.platform.packages

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.access.UsageAccess
import dev.lumbina.tv.core.domain.app.AppUsage
import dev.lumbina.tv.core.domain.app.LaunchHistory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

/**
 * Openings over the last two weeks, counted from [UsageStatsManager] events: an app coming to
 * the front after another one (moving between its own screens doesn't count). Needs usage access, which
 * TVs rarely offer in their settings: `adb shell appops set dev.lumbina.tv GET_USAGE_STATS allow`.
 * Without it, the apps Lumbina itself opened ([LaunchHistory]). Access is re-checked while observed,
 * so granting or revoking it switches the source. One loop per process, shared by all observers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
internal class AndroidAppUsage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val launches: LaunchHistory,
    private val access: UsageAccess,
) : AppUsage {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val openings: Flow<Map<String, Long>> = flow {
        while (true) {
            emit(access.granted())
            delay(ACCESS_CHECK_MILLIS)
        }
    }.distinctUntilChanged()
        .flatMapLatest { allowed -> if (allowed) usageStats() else launches.openings() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    override fun recent(): Flow<Map<String, Long>> = openings

    private fun usageStats(): Flow<Map<String, Long>> = flow {
        while (true) {
            emit(read())
            delay(REFRESH_MILLIS)
        }
    }

    private fun read(): Map<String, Long> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val openings = mutableMapOf<String, Long>()
        runCatching {
            val events = manager.queryEvents(now - PERIOD_MILLIS, now)
            val event = UsageEvents.Event()
            var front: String? = null
            while (events.getNextEvent(event)) {
                if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED || event.packageName == front) continue
                front = event.packageName
                openings.merge(event.packageName, 1L, Long::plus)
            }
        }
        return openings
    }

    private companion object {
        const val PERIOD_MILLIS = 14 * 24 * 60 * 60_000L
        const val REFRESH_MILLIS = 30 * 60_000L
        const val ACCESS_CHECK_MILLIS = 60_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
