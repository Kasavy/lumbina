package dev.lumbina.tv.core.testing

import dev.lumbina.tv.core.domain.health.HealLock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** [HealLock] within one test: heals take turns, as they do across processes. */
class FakeHealLock : HealLock {
    private val mutex = Mutex()

    override suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
