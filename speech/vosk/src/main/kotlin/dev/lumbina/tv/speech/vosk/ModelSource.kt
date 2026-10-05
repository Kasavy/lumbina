package dev.lumbina.tv.speech.vosk

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/** An open download: [stream] yields the file's bytes starting at [offset]. */
internal class Opened(val offset: Long, val stream: InputStream) : Closeable {
    override fun close() = stream.close()
}

/** Where model archives come from. */
internal fun interface ModelSource {
    /** Opens [url] at byte [offset]; a server that ignores the range restarts at 0. */
    @Throws(IOException::class)
    fun open(url: String, offset: Long): Opened
}

internal class HttpModelSource @Inject constructor() : ModelSource {
    override fun open(url: String, offset: Long): Opened {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
        return when (val code = connection.responseCode) {
            HttpURLConnection.HTTP_PARTIAL -> Opened(offset, connection.inputStream)

            HttpURLConnection.HTTP_OK -> Opened(0, connection.inputStream)

            else -> {
                connection.disconnect()
                throw IOException("HTTP $code for $url")
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 15_000
    }
}
