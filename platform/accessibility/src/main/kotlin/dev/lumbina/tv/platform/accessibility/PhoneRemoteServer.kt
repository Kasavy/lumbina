package dev.lumbina.tv.platform.accessibility

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.json.JSONObject

/** A request to the phone remote page's server: `/api/<command>?…` with an optional body. */
internal class PhoneRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: InputStream,
    val contentLength: Long,
) {
    val command: String? get() = path.removePrefix("/api/").takeIf { path.startsWith("/api/") }
}

/** How a command went, as the page reads it: `{"ok":…}`, with why it failed when the page can say so. */
internal class PhoneAnswer private constructor(val json: String) {
    companion object {
        val Done = PhoneAnswer("""{"ok":true}""")
        val Failed = PhoneAnswer("""{"ok":false}""")

        /** The device's Android can't do this (the D-pad needs 13). */
        val Unsupported = PhoneAnswer("""{"ok":false,"error":"sdk"}""")

        fun of(ok: Boolean) = if (ok) Done else Failed

        /** Refused for a reason the page words itself ([error]), with [detail] to show as it is. */
        fun refused(error: String, detail: String? = null): PhoneAnswer {
            val json = JSONObject().put("ok", false).put("error", error)
            detail?.let { json.put("detail", it) }
            return PhoneAnswer(json.toString())
        }
    }
}

/**
 * The phone remote's web server: plain HTTP on the local network, one request per connection (the
 * page sends a request per button press or touchpad move, and one per file it sends). Serves the page to
 * anyone; the API answers only requests whose `X-Key` header carries the [key] from the QR code,
 * and the last diagnostics report ([report]) is given to an address that carries it (a phone's
 * camera opens an address, it sends no headers).
 * Serves a few connections at a time, so stuck clients can't take up every I/O thread. A client
 * without the key waits with its request for at most [HEAD_MILLIS] (the head must arrive by then)
 * and holds at most [MAX_PER_ADDRESS] slots, so one device on the network can't keep the phone out. Several
 * devices acting together still can: the network is trusted that far.
 */
internal class PhoneRemoteServer(
    private val port: Int,
    private val key: String,
    private val page: () -> ByteArray,
    private val handle: suspend (PhoneRequest) -> PhoneAnswer,
    private val log: LumbinaLog,
    private val report: () -> ByteArray? = { null },
    /** What the page asks first: the device's name and what it can do, as JSON. */
    private val info: () -> String = { PhoneAnswer.Done.json },
) {
    private val keyBytes = key.toByteArray()
    private val connections = Semaphore(MAX_CONNECTIONS)
    private val perAddress = mutableMapOf<InetAddress, Int>()

    @Volatile private var socket: ServerSocket? = null
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            val server = try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
            } catch (e: IOException) {
                log.log(TAG, "cannot listen on $port: $e")
                return@launch
            }
            socket = server
            // stop() may have run while binding, before there was a socket to close.
            if (!isActive) return@launch server.close()
            log.log(TAG, "phone remote listening on $port")
            server.use { accept(it) }
        }
    }

    private suspend fun CoroutineScope.accept(server: ServerSocket) {
        while (isActive) {
            val client = try {
                server.accept()
            } catch (_: SocketException) {
                return // closed by stop()
            }
            val address = client.inetAddress
            if (!admit(address)) {
                // Said once in a while: a phone that can't open the page must leave a trace of why.
                noteRefused(address)
                runCatching { client.close() }
                continue
            }
            connections.acquire()
            launch {
                try {
                    serve(client)
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    // A request must never take the service down with it.
                    log.log(TAG, "request failed: $e")
                } finally {
                    connections.release()
                    leave(address)
                }
            }
        }
    }

    private var refusedLoggedAt = 0L

    private fun noteRefused(address: InetAddress) {
        val now = System.currentTimeMillis()
        if (now - refusedLoggedAt < REFUSED_LOG_MILLIS) return
        refusedLoggedAt = now
        log.log(TAG, "too many connections from ${address.hostAddress}: closed one")
    }

    private fun admit(address: InetAddress): Boolean = synchronized(perAddress) {
        val open = perAddress[address] ?: 0
        if (open >= MAX_PER_ADDRESS) return false
        perAddress[address] = open + 1
        true
    }

    private fun leave(address: InetAddress) = synchronized(perAddress) {
        val open = (perAddress[address] ?: 1) - 1
        if (open > 0) perAddress[address] = open else perAddress.remove(address)
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        job?.cancel()
        job = null
    }

    private suspend fun serve(client: Socket) = client.use {
        val input = HeadDeadline(BufferedInputStream(client.getInputStream()), HEAD_MILLIS, { client.soTimeout = it })
        val output = client.getOutputStream()
        val request = try {
            readRequest(input).also {
                input.headRead()
                client.soTimeout = READ_TIMEOUT_MILLIS
            }
        } catch (e: IOException) {
            log.log(TAG, "bad request: $e")
            null
        } ?: return@use respond(output, STATUS_BAD_REQUEST, "text/plain", "bad request".toByteArray())
        when {
            request.method == "GET" && (request.path == "/" || request.path == "/index.html") -> {
                log.log(TAG, "page opened from ${client.inetAddress.hostAddress}")
                respond(output, STATUS_OK, "text/html; charset=utf-8", page())
            }

            request.method == "GET" && request.path == PhoneRemoteSettings.REPORT_PATH -> giveReport(output, request)

            request.command == null -> respond(output, STATUS_NOT_FOUND, "text/plain", "not found".toByteArray())

            !MessageDigest.isEqual(request.headers["x-key"]?.toByteArray(), keyBytes) ->
                respond(output, STATUS_FORBIDDEN, JSON, """{"ok":false,"error":"key"}""".toByteArray())

            request.command == "info" -> respond(output, STATUS_OK, JSON, info().toByteArray())

            else -> {
                val answer = runCatching { handle(request) }.onFailure { log.log(TAG, "${request.command}: $it") }
                    .getOrDefault(PhoneAnswer.Failed)
                respond(output, STATUS_OK, JSON, answer.json.toByteArray())
            }
        }
        linger(client, request.body)
    }

    /**
     * What is left of a body answered before it was read (a file refused for its size or name) is
     * read and dropped, for a while: closed with it unread, the connection is reset and the browser
     * gets an error in place of the answer that says why.
     */
    private fun linger(client: Socket, body: InputStream) {
        runCatching {
            client.shutdownOutput()
            client.soTimeout = LINGER_READ_MILLIS
            val until = System.currentTimeMillis() + LINGER_MILLIS
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var open = true
            while (open && System.currentTimeMillis() < until) open = body.read(buffer) >= 0
        }
    }

    /** The report as a file to save or share: that is what a phone's browser then offers. */
    private fun giveReport(output: OutputStream, request: PhoneRequest) {
        // The key first: nobody without it makes the service read the file.
        val keyed = MessageDigest.isEqual(request.query["k"]?.toByteArray(), keyBytes)
        val text = if (keyed) report() else null
        if (text == null) return respond(output, STATUS_NOT_FOUND, "text/plain", "not found".toByteArray())
        val asFile = "Content-Disposition: attachment; filename=\"${PhoneRemoteSettings.REPORT_FILE}\"\r\n"
        respond(output, STATUS_OK, "text/plain; charset=utf-8", text, asFile)
    }

    private fun respond(output: OutputStream, status: String, type: String, body: ByteArray, more: String = "") {
        val head = "HTTP/1.1 $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n$more" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        runCatching {
            output.write(head.toByteArray())
            output.write(body)
            output.flush()
        }
    }

    private companion object {
        const val TAG = "phone"
        const val READ_TIMEOUT_MILLIS = 30_000
        const val HEAD_MILLIS = 10_000L
        const val REFUSED_LOG_MILLIS = 10_000L
        const val LINGER_MILLIS = 3_000L
        const val LINGER_READ_MILLIS = 1_000

        // A phone's browser keeps several connections open at once, some of them idle ones it opened
        // ahead of need: with fewer slots than it uses, the page stops opening on that phone.
        const val MAX_CONNECTIONS = 12
        const val MAX_PER_ADDRESS = 6
        const val JSON = "application/json"
        const val STATUS_OK = "200 OK"
        const val STATUS_BAD_REQUEST = "400 Bad Request"
        const val STATUS_FORBIDDEN = "403 Forbidden"
        const val STATUS_NOT_FOUND = "404 Not Found"
    }
}

/** Reads the request line and headers; the body stays in [input], [PhoneRequest.contentLength] long. */
internal fun readRequest(input: InputStream): PhoneRequest? {
    val headLines = generateSequence { readLine(input) }.takeWhile { it.isNotEmpty() }.take(MAX_HEAD_LINES + 1).toList()
    if (headLines.size > MAX_HEAD_LINES) throw IOException("too many header lines")
    val (method, target) = headLines.firstOrNull()?.split(' ')?.takeIf { it.size >= 2 } ?: return null
    val headers = headLines.drop(1).mapNotNull { line ->
        val colon = line.indexOf(':').takeIf { it > 0 } ?: return@mapNotNull null
        line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
    }.toMap()
    val length = headers["content-length"]?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L
    val body = LimitedInput(input, length)
    return PhoneRequest(method, target.substringBefore('?'), queryOf(target), headers, body, length)
}

/** The query parameters; malformed ones (bad %-escapes) are left out. */
internal fun queryOf(target: String): Map<String, String> = target.substringAfter('?', "")
    .split('&')
    .filter { it.isNotEmpty() }
    .mapNotNull { part ->
        runCatching {
            URLDecoder.decode(part.substringBefore('='), "UTF-8") to
                URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        }.getOrNull()
    }
    .toMap()

private const val MAX_LINE = 8 * 1024
private const val MAX_HEAD_LINES = 64

/** One CRLF-terminated line of the request head, or null at the end of the stream. */
private fun readLine(input: InputStream): String? {
    val line = StringBuilder()
    while (line.length < MAX_LINE) {
        val c = input.read()
        if (c < 0) return line.takeIf { it.isNotEmpty() }?.toString()
        if (c == '\n'.code) return line.toString().trimEnd('\r')
        line.append(c.toChar())
    }
    throw IOException("header line too long")
}

/**
 * One deadline, [limitMillis] from now, for the whole request head: before each read the socket's
 * timeout ([setTimeout]) is cut to what is left, so neither a silent client nor one sending a
 * byte at a time holds on longer. After [headRead] reads go through untouched.
 */
internal class HeadDeadline(
    private val input: InputStream,
    limitMillis: Long,
    private val setTimeout: (Int) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : InputStream() {
    private var until: Long? = now() + limitMillis

    fun headRead() {
        until = null
    }

    private fun limit() {
        val left = (until ?: return) - now()
        if (left <= 0) throw IOException("request head too slow")
        setTimeout(left.toInt())
    }

    override fun read(): Int {
        limit()
        return input.read()
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        limit()
        return input.read(buffer, offset, count)
    }
}

/** Exposes only the request body: reading stops after [length] bytes. */
private class LimitedInput(private val input: InputStream, private var length: Long) : InputStream() {
    override fun read(): Int {
        if (length <= 0) return -1
        return input.read().also { if (it >= 0) length-- }
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        if (length <= 0) return -1
        val read = input.read(buffer, offset, minOf(count.toLong(), length).toInt())
        if (read > 0) length -= read
        return read
    }
}
