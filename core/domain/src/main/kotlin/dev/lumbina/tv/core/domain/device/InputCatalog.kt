package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The device's video inputs for every screen of Lumbina, under the names the user gave them;
 * empty where the device has none Lumbina reaches. Asked of the device once they are there; until
 * then again a few times, as a device may list its inputs only a while after Lumbina starts.
 * The one poll of [live], for every screen that shows it, runs in [sharing].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class InputCatalog internal constructor(
    private val profile: DeviceProfile,
    layouts: HomeLayoutRepository,
    sharing: CoroutineScope,
) {
    @Inject constructor(profile: DeviceProfile, layouts: HomeLayoutRepository) :
        this(profile, layouts, CoroutineScope(SupervisorJob()))

    private val found = MutableStateFlow<List<InputSource>?>(null)
    private val asking = Mutex()

    /** The device's last answer: a screen shown again starts from it, not from nothing on any input. */
    @Volatile private var lastPresence = emptyMap<String, InputPresence>()

    /** None at first, so nothing waits for the device; then the inputs as soon as they are known. */
    val inputs: Flow<List<InputSource>> = combine(
        known(),
        layouts.layout.map { it.inputLabels }.distinctUntilChanged(),
    ) { inputs, labels -> inputs.map { input -> labels[input.id]?.let { input.copy(name = it) } ?: input } }
        .distinctUntilChanged()

    /**
     * [inputs] with what is on each now, asked of the device again every few seconds while some
     * screen shows it: one poll however many show it, none while there are no inputs.
     */
    val live: Flow<List<InputSource>> = inputs.flatMapLatest { inputs ->
        if (inputs.isEmpty()) {
            flowOf(inputs)
        } else {
            polled().map { presence -> inputs.map { it.copy(presence = presence[it.id]) } }
        }
    }
        .distinctUntilChanged()
        .shareIn(sharing, SharingStarted.WhileSubscribed(STOP_MILLIS), replay = 1)

    /** What is on each input now, asked once; empty where the device can't tell. */
    suspend fun presence(): Map<String, InputPresence> = profile.inputs?.presence().orEmpty()

    /** Switches to input [id] and shows it full screen; false when it could not. */
    suspend fun open(id: String): Boolean = profile.inputs?.open(id) == true

    /**
     * Asked again and again, from the last answer on. After an empty one only now and then: the
     * device may not tell at all, or not just now (its service not up yet, nothing sending a name).
     */
    private fun polled(): Flow<Map<String, InputPresence>> = flow {
        emit(lastPresence)
        val sources = profile.inputs ?: return@flow
        while (true) {
            val presence = sources.presence()
            lastPresence = presence
            emit(presence)
            delay(if (presence.isEmpty()) QUIET_MILLIS else PRESENCE_MILLIS)
        }
    }.distinctUntilChanged()

    private fun known(): Flow<List<InputSource>> = flow {
        found.value?.let {
            emit(it)
            return@flow
        }
        emit(emptyList())
        val sources = profile.inputs ?: return@flow
        repeat(TRIES) {
            val inputs = asking.withLock {
                found.value ?: sources.list().takeIf { it.isNotEmpty() }?.also { found.value = it }
            }
            if (inputs != null) {
                emit(inputs)
                return@flow
            }
            delay(RETRY_MILLIS)
        }
    }

    private companion object {
        const val TRIES = 6
        const val RETRY_MILLIS = 10_000L
        const val PRESENCE_MILLIS = 5_000L
        const val QUIET_MILLIS = 60_000L

        /** A screen left and opened again at once (a dialog over it) keeps the same poll. */
        const val STOP_MILLIS = 5_000L
    }
}
