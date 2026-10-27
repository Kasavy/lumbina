package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The device's video inputs for every screen of Lumbina, under the names the user gave them;
 * empty where the device has none Lumbina reaches. Asked of the device once they are there; until
 * then again a few times, as a device may list its inputs only a while after Lumbina starts.
 */
@Singleton
class InputCatalog @Inject constructor(private val profile: DeviceProfile, layouts: HomeLayoutRepository) {
    private val found = MutableStateFlow<List<InputSource>?>(null)
    private val asking = Mutex()

    /** None at first, so nothing waits for the device; then the inputs as soon as they are known. */
    val inputs: Flow<List<InputSource>> = combine(
        known(),
        layouts.layout.map { it.inputLabels }.distinctUntilChanged(),
    ) { inputs, labels -> inputs.map { input -> labels[input.id]?.let { input.copy(name = it) } ?: input } }
        .distinctUntilChanged()

    /** Switches to input [id] and shows it full screen; false when it could not. */
    suspend fun open(id: String): Boolean = profile.inputs?.open(id) == true

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
    }
}
