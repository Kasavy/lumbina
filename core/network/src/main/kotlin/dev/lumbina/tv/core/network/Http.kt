package dev.lumbina.tv.core.network

import dev.lumbina.tv.core.common.LumbinaLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A server's answer other than 200: [code] says whether asking again could help (5xx) or not (most 4xx). */
class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

/** GETs a URL; any failure is an [IOException], an HTTP status other than 200 an [HttpStatusException]. */
fun interface HttpGet {
    suspend fun bytes(url: String): ByteArray

    suspend fun text(url: String): String = bytes(url).decodeToString()
}

/** [HttpGet] over [HttpURLConnection], with timeouts and an identifying User-Agent (api.met.no asks for one). */
class UrlConnectionGet @Inject constructor(private val log: LumbinaLog) : HttpGet {
    override suspend fun bytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw HttpStatusException(code, url.substringBefore('?'))
            connection.inputStream.use { it.readBytes() }
        } catch (e: IOException) {
            log.log(TAG, "${url.substringBefore('?')}: $e")
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val USER_AGENT = "LumbinaTV (Android launcher)"
        const val TAG = "http"
    }
}
