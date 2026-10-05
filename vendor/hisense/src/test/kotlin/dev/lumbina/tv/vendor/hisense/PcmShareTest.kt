package dev.lumbina.tv.vendor.hisense

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmShareTest {

    /** Scripted service: replies to each command in order. */
    private class FakeTransport(vararg replies: ByteArray) : PcmShareTransport {
        val sent = mutableListOf<String>()
        var closed = false
        private val queue = ArrayDeque(replies.toList())

        override fun send(command: String) {
            sent += command
        }

        override fun receive(dst: ByteArray): Int {
            val reply = queue.removeFirstOrNull() ?: return -1
            reply.copyInto(dst)
            return reply.size
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `session starts, reads audio a frame at a time, treats a lone zero byte as no data, and stops`() {
        val transport = FakeTransport("OK#659#".toByteArray(), byteArrayOf(0), ByteArray(1280) { 7 })
        val stream = PcmShareAudioSource { transport }.open()
        val buf = ByteArray(6400)

        assertEquals(0, stream.read(buf))
        assertEquals(1280, stream.read(buf))
        stream.close()

        assertEquals(
            listOf(
                "start_pcmrecorder_v_0#lumbina#0#4",
                "get_pcmdata_v_0#1280#659",
                "get_pcmdata_v_0#1280#659",
                "stop_pcmrecorder_v_0#659#4",
            ),
            transport.sent,
        )
        assertTrue(transport.closed)
    }

    @Test
    fun `a refused start closes the connection`() {
        val transport = FakeTransport("ER".toByteArray())
        assertThrows(IOException::class.java) { PcmShareAudioSource { transport }.open() }
        assertTrue(transport.closed)
    }

    @Test
    fun `client id parsing`() {
        assertEquals("899", PcmShareProtocol.clientId("OK#899#"))
        assertThrows(IOException::class.java) { PcmShareProtocol.clientId("OK##") }
        assertThrows(IOException::class.java) { PcmShareProtocol.clientId("") }
    }
}
