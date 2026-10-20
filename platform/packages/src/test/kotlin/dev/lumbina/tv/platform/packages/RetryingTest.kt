package dev.lumbina.tv.platform.packages

import dev.lumbina.tv.core.network.HttpStatusException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryingTest {

    private val pauses = listOf(2_000L, 5_000L)
    private val noted = mutableListOf<IOException>()

    /** Fails with [failures] in turn, then answers "done". */
    private class Server(vararg failures: IOException) {
        private val left = failures.toMutableList()
        var calls = 0

        fun answer(): String {
            calls++
            if (left.isNotEmpty()) throw left.removeAt(0)
            return "done"
        }
    }

    @Test
    fun `network failures are tried again after each pause, and the answer comes`() = runTest {
        val server = Server(SocketTimeoutException("SSL handshake timed out"), SSLException("reset"))
        assertEquals("done", retrying(pauses, { noted += it }) { server.answer() })
        assertEquals(3, server.calls)
        assertEquals(2, noted.size)
        assertEquals(7_000L, currentTime)
    }

    @Test
    fun `after the last try its failure is the answer, with no pause after it`() = runTest {
        val server = Server(*Array(3) { SocketTimeoutException("timeout $it") })
        val failure = runCatching { retrying(pauses, { noted += it }) { server.answer() } }.exceptionOrNull()
        assertEquals("timeout 2", failure?.message)
        assertEquals(3, server.calls)
        assertEquals(2, noted.size)
        assertEquals(7_000L, currentTime)
    }

    @Test
    fun `a missing file, a refusal or a final answer is not asked again`() = runTest {
        val finals = listOf(
            HttpStatusException(404, "https://example.org/a.apk"),
            HttpStatusException(403, "https://example.org/a.apk"),
            FinalAnswer("more than the published 46 bytes"),
        )
        for (failure in finals) {
            val server = Server(failure)
            assertTrue(runCatching { retrying(pauses, { noted += it }) { server.answer() } }.isFailure)
            assertEquals(1, server.calls)
        }
        assertTrue(noted.isEmpty())
    }

    @Test
    fun `a busy server is asked again`() = runTest {
        for (code in listOf(503, 429, 408)) {
            val server = Server(HttpStatusException(code, "https://example.org/a.apk"))
            assertEquals("done", retrying(pauses, { noted += it }) { server.answer() })
            assertEquals(2, server.calls)
        }
    }
}
