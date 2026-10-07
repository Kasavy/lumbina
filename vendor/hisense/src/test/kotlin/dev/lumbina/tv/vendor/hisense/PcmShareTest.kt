package dev.lumbina.tv.vendor.hisense

import dev.lumbina.tv.core.domain.device.DeviceAnswer
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

    /** A probe of one route, "socket", whose connections answer by [scripts] in turn; they land in [connections]. */
    private fun probe(connections: MutableList<FakeTransport>, vararg scripts: Array<ByteArray>): PcmShareProbe {
        val left = ArrayDeque(scripts.toList())
        return PcmShareProbe(listOf("socket" to { FakeTransport(*left.removeFirst()).also { connections += it } }))
    }

    @Test
    fun `the probe asks the format and both spellings of start, stopping each as it was started`() {
        val connections = mutableListOf<FakeTransport>()
        val answers = probe(
            connections,
            arrayOf("#SAMPLERATE:16000#BUFFERSIZE:1280#".toByteArray()),
            arrayOf("OK#5#".toByteArray(), "OK".toByteArray()),
            arrayOf("OK#7#".toByteArray(), "OK".toByteArray()),
        ).run()

        assertEquals(
            listOf(
                DeviceAnswer("socket: get_pcminfo_v_0#0#4#", "#SAMPLERATE:16000#BUFFERSIZE:1280#"),
                DeviceAnswer("socket: start_pcmrecorder_v_0#lumbina#0#4", "OK#5#, then stopped: OK"),
                DeviceAnswer("socket: start_pcmrecorder_v_0#lumbina#16000#4#", "OK#7#, then stopped: OK"),
            ),
            answers,
        )
        assertEquals(listOf("start_pcmrecorder_v_0#lumbina#0#4", "stop_pcmrecorder_v_0#5#4"), connections[1].sent)
        assertEquals(listOf("start_pcmrecorder_v_0#lumbina#16000#4#", "stop_pcmrecorder_v_0#7#4#"), connections[2].sent)
        assertTrue(connections.all { it.closed })
    }

    @Test
    fun `the probe keeps a start's answer when the stop gets none, and tells a refusal as it came`() {
        val connections = mutableListOf<FakeTransport>()
        val answers = probe(
            connections,
            arrayOf(byteArrayOf(0)),
            arrayOf("OK#5#".toByteArray()),
            arrayOf("ER".toByteArray()),
        ).run()

        assertEquals(listOf(".", "OK#5#, then stopped: (closed the connection)", "ER"), answers.map { it.answer })
        assertTrue(connections.all { it.closed })
    }

    @Test
    fun `a route that refuses the connection is asked once, and the next is asked until one connects`() {
        var spare = 0
        val answers = PcmShareProbe(
            listOf(
                "socket" to { throw IOException("Connection refused") },
                "port" to { FakeTransport() },
                "spare" to { FakeTransport().also { spare++ } },
            ),
        ).run()

        assertEquals(
            DeviceAnswer("socket: get_pcminfo_v_0#0#4#", "no connection: java.io.IOException: Connection refused"),
            answers.first(),
        )
        assertEquals(listOf("socket", "port", "port", "port"), answers.map { it.question.substringBefore(':') })
        assertEquals(0, spare)
    }

    @Test
    fun `nothing is asked while the remote is being read`() {
        val connections = mutableListOf<FakeTransport>()
        val answers = PcmShareProbe(
            listOf("socket" to { FakeTransport().also { connections += it } }),
            micInUse = { true },
        ).run()

        assertEquals(1, answers.size)
        assertTrue(answers.single().answer.startsWith("not asked"))
        assertTrue(connections.isEmpty())
    }

    @Test
    fun `a stream holds the in-use mark from a start that took until it is closed`() {
        var held = 0
        val inUse = {
            held++
            AutoCloseable { held-- }
        }
        val stream = PcmShareAudioSource(inUse) { FakeTransport("OK#1#".toByteArray()) }.open()
        assertEquals(1, held)
        stream.close()
        assertEquals(0, held)

        val refused = PcmShareAudioSource(inUse) { FakeTransport("ER".toByteArray()) }
        assertThrows(IOException::class.java) { refused.open() }
        assertEquals(0, held)
    }

    @Test
    fun `client id parsing`() {
        assertEquals("899", PcmShareProtocol.clientId("OK#899#"))
        assertThrows(IOException::class.java) { PcmShareProtocol.clientId("OK##") }
        assertThrows(IOException::class.java) { PcmShareProtocol.clientId("") }
    }
}
