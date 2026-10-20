package dev.lumbina.tv.platform.packages

import com.sun.net.httpserver.HttpServer
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.update.Release
import dev.lumbina.tv.core.network.HttpGet
import dev.lumbina.tv.core.network.HttpStatusException
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReleaseDownloadTest {

    private val apk = ByteArray(300_000) { (it % 251).toByte() }
    private val sum = MessageDigest.getInstance("SHA-256").digest(apk).joinToString("") { "%02x".format(it) }
    private val file = File(createTempDirectory().toFile(), "lumbina.apk")

    /** The statuses the package is answered with, in turn; then 200, or 206 for a range. */
    private val statuses = ArrayDeque<Int>()
    private val ranges = mutableListOf<String?>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/lumbina.apk") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")
            ranges += range
            val status = statuses.removeFirstOrNull()
            val from = range?.removePrefix("bytes=")?.removeSuffix("-")?.toInt()
            when {
                status != null -> exchange.sendResponseHeaders(status, -1)

                from != null -> {
                    exchange.sendResponseHeaders(206, (apk.size - from).toLong())
                    exchange.responseBody.use { it.write(apk, from, apk.size - from) }
                }

                else -> {
                    exchange.sendResponseHeaders(200, apk.size.toLong())
                    exchange.responseBody.use { it.write(apk) }
                }
            }
            exchange.close()
        }
        start()
    }

    private fun release(bytes: Long = apk.size.toLong()) = Release(
        version = "0.0.6",
        apkName = "lumbina.apk",
        apkUrl = "http://127.0.0.1:${server.address.port}/lumbina.apk",
        bytes = bytes,
        sumsUrl = "http://127.0.0.1:${server.address.port}/SHA256SUMS.txt",
    )

    private fun download(published: String = sum) =
        ReleaseDownload(HttpGet { "$published  lumbina.apk\n".toByteArray() }, LumbinaLog { _, _ -> })

    private val shown = mutableListOf<Float>()

    @After
    fun stop() = server.stop(0)

    @Test
    fun `the package comes whole, its sum is the published one`() = runTest {
        assertTrue(download().fetch(release(), file) { shown += it })
        assertArrayEquals(apk, file.readBytes())
        assertEquals(1f, shown.last())
    }

    @Test
    fun `a package already here and whole is not asked for again`() = runTest {
        file.writeBytes(apk)
        assertTrue(download().fetch(release(), file) {})
        assertTrue(ranges.isEmpty())
    }

    @Test
    fun `a download cut short goes on from where it stopped`() = runTest {
        file.writeBytes(apk.copyOf(100_000))
        assertTrue(download().fetch(release(), file) { shown += it })
        assertEquals(listOf("bytes=100000-"), ranges)
        assertArrayEquals(apk, file.readBytes())
        // It goes on from a third: what is shown starts there, not at nothing.
        assertTrue(shown.first() > 0.33f)
    }

    @Test
    fun `a whole package that is not the published one is downloaded again`() = runTest {
        file.writeBytes(ByteArray(apk.size))
        assertTrue(download().fetch(release(), file) {})
        assertEquals(listOf(null), ranges)
    }

    @Test
    fun `a busy server is asked again, a missing file is not`() = runTest {
        statuses += 503
        assertTrue(download().fetch(release(), file) {})
        assertEquals(2, ranges.size)

        file.delete()
        ranges.clear()
        statuses += 404
        val failure = runCatching { download().fetch(release(), file) {} }.exceptionOrNull()
        assertEquals(404, (failure as? HttpStatusException)?.code)
        assertEquals(1, ranges.size)
    }

    @Test
    fun `more than the release said it has is no package of it`() = runTest {
        val failure = runCatching { download().fetch(release(bytes = 1_000), file) {} }.exceptionOrNull()
        assertTrue(failure is FinalAnswer)
    }

    @Test
    fun `a package whose sum differs is no package of the release`() = runTest {
        assertFalse(download(published = "0".repeat(64)).fetch(release(), file) {})
    }
}
