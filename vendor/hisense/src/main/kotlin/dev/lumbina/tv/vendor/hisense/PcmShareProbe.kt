package dev.lumbina.tv.vendor.hisense

import dev.lumbina.tv.core.domain.device.DeviceAnswer
import java.io.IOException

/**
 * For a diagnostics report: whether HsPcmShare listens on any of [routes] (a name, and how to
 * connect), and which spelling of "start" it takes. The routes are tried in turn until one
 * connects: the service was seen to keep a connection to its port after the probe closed it, so
 * the port is asked only where the socket is not there. A recorder that starts is stopped at
 * once, unread. The remote has room for one reader, so nothing is asked while Lumbina reads it
 * ([micInUse]): a second recorder could cut the phrase being said, and a refusal caused by the
 * first would read as the firmware's.
 */
internal class PcmShareProbe(
    private val routes: List<Pair<String, () -> PcmShareTransport>>,
    private val micInUse: () -> Boolean = { false },
) {
    fun run(): List<DeviceAnswer> {
        if (micInUse()) return listOf(DeviceAnswer("remote mic service", IN_USE))
        val answers = mutableListOf<DeviceAnswer>()
        for ((name, connect) in routes) {
            if (onRoute(name, connect, answers)) break
        }
        return answers
    }

    /** Whether anybody listened there; if not, the refusal is told once. */
    private fun onRoute(name: String, connect: () -> PcmShareTransport, answers: MutableList<DeviceAnswer>): Boolean {
        val info = PcmShareProtocol.info()
        val asked = ask(connect) { exchange(it, info) }
        answers += DeviceAnswer("$name: $info", asked.text)
        if (!asked.connected) return false
        val plain = PcmShareProtocol.start()
        answers += DeviceAnswer("$name: $plain", ask(connect) { start(it, plain, PcmShareProtocol::stop) }.text)
        val rated = PcmShareProtocol.startRated()
        answers += DeviceAnswer("$name: $rated", ask(connect) { start(it, rated, PcmShareProtocol::stopRated) }.text)
        return true
    }

    private class Asked(val text: String, val connected: Boolean)

    private fun ask(connect: () -> PcmShareTransport, what: (PcmShareTransport) -> String): Asked {
        val transport = try {
            connect()
        } catch (e: IOException) {
            return Asked("no connection: $e", connected = false)
        }
        return Asked(transport.use { answerOf { what(it) } }, connected = true)
    }

    /** The reply to "start" and, if it started, to the "stop" spelled like it. */
    private fun start(transport: PcmShareTransport, command: String, stop: (String) -> String): String {
        val reply = exchange(transport, command)
        val id = runCatching { PcmShareProtocol.clientId(reply) }.getOrNull() ?: return reply
        // The start's answer is the news; a stop left unanswered must not take it away.
        return "$reply, then stopped: ${answerOf { exchange(transport, stop(id)) }}"
    }

    private fun answerOf(exchange: () -> String): String = try {
        exchange()
    } catch (e: IOException) {
        "no answer: $e"
    }

    private fun exchange(transport: PcmShareTransport, command: String): String {
        transport.send(command)
        val reply = ByteArray(REPLY_BYTES)
        val n = transport.receive(reply)
        if (n < 0) return "(closed the connection)"
        // The service may answer with audio or a lone zero byte: keep the report to text.
        return String(reply, 0, n, Charsets.US_ASCII).map { if (it in ' '..'~') it else '.' }.joinToString("")
    }

    private companion object {
        const val REPLY_BYTES = 256
        const val IN_USE = "not asked: voice input is reading the remote; save the report again in a minute"
    }
}
