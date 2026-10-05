package dev.lumbina.tv.platform.accessibility

import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneRemoteServerTest {

    @Test
    fun `reads the request head and exactly the body`() {
        val raw = "POST /api/text?k=abc&x=%D0%BA%D0%BE%D1%82 HTTP/1.1\r\nHost: tv\r\nContent-Length: 5\r\n" +
            "X-Key: abc\r\n\r\nhelloEXTRA"
        val request = readRequest(raw.byteInputStream())!!
        assertEquals("POST", request.method)
        assertEquals("/api/text", request.path)
        assertEquals("text", request.command)
        assertEquals(mapOf("k" to "abc", "x" to "кот"), request.query)
        assertEquals("abc", request.headers["x-key"])
        assertEquals("hello", request.body.readBytes().decodeToString())
    }

    @Test
    fun `the page is not a command`() {
        val request = readRequest("GET / HTTP/1.1\r\n\r\n".byteInputStream())!!
        assertNull(request.command)
        assertEquals(emptyMap<String, String>(), request.query)
    }

    @Test
    fun `garbage is not a request`() {
        assertNull(readRequest("".byteInputStream()))
        assertNull(readRequest("nonsense\r\n\r\n".byteInputStream()))
    }

    @Test
    fun `reads a large upload`() {
        val body = ByteArray(2_436_554) { (it % 251).toByte() }
        val head = "POST /api/upload?k=x&name=a.png HTTP/1.1\r\nHost: 127.0.0.1:18765\r\nUser-Agent: curl/8.1.2\r\n" +
            "Accept: */*\r\nContent-Length: ${body.size}\r\nContent-Type: application/x-www-form-urlencoded\r\n\r\n"
        val request = readRequest(java.io.BufferedInputStream((head.toByteArray() + body).inputStream()))!!
        assertEquals(body.size.toLong(), request.contentLength)
        assertEquals(body.size, request.body.readBytes().size)
    }

    @Test
    fun `malformed escapes in the query are skipped`() {
        assertEquals(mapOf("b" to "ok"), queryOf("/api/key?%zz=1&b=ok&c=%"))
    }

    @Test
    fun `a head trickled in slower than the deadline is refused, the body after it is not`() {
        var clock = 0L
        val raw = "POST /api/text HTTP/1.1\r\nContent-Length: 4\r\n\r\nabcd"
        val trickle =
            HeadDeadline(raw.byteInputStream(), 10_000, setTimeout = {}, now = { clock.also { clock += 1_000 } })
        assertThrows(IOException::class.java) { readRequest(trickle) }

        clock = 0
        val quick = HeadDeadline(raw.byteInputStream(), 10_000, setTimeout = {}, now = { clock })
        val request = readRequest(quick)!!.also { quick.headRead() }
        clock = 60_000
        assertEquals("abcd", request.body.readBytes().decodeToString())
    }

    @Test
    fun `a client that sends nothing is let go when the head's time is up`() {
        ServerSocket(0).use { server ->
            Socket("127.0.0.1", server.localPort).use {
                server.accept().use { client ->
                    val input = HeadDeadline(client.getInputStream(), 300, { client.soTimeout = it })
                    val started = System.currentTimeMillis()
                    assertThrows(IOException::class.java) { readRequest(input) }
                    assertTrue(System.currentTimeMillis() - started < 2_000)
                }
            }
        }
    }

    @Test
    fun `a head with too many lines is refused`() {
        val raw = "GET / HTTP/1.1\r\n" + "X: y\r\n".repeat(100) + "\r\n"
        assertThrows(IOException::class.java) { readRequest(raw.byteInputStream()) }
    }
}
