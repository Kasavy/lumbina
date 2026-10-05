package dev.lumbina.tv.platform.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FileHealLockTest {

    @Test
    fun `heals in one process take turns instead of failing on the file lock`() = runBlocking {
        val lock = FileHealLock(RuntimeEnvironment.getApplication())
        var inside = 0
        var most = 0
        List(3) {
            async(Dispatchers.Default) {
                lock.withLock {
                    most = maxOf(most, ++inside)
                    delay(50)
                    inside--
                }
            }
        }.awaitAll()
        assertEquals(1, most)
    }
}
