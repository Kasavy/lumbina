package dev.lumbina.tv.speech.cloud

import dev.lumbina.tv.core.common.CYRILLIC_LANGUAGES
import dev.lumbina.tv.core.domain.speech.CloudSpeechEngine
import dev.lumbina.tv.core.domain.speech.Hypothesis
import dev.lumbina.tv.core.domain.speech.Pcm
import dev.lumbina.tv.core.domain.speech.SpeechSession
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable.Reason
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONException
import org.json.JSONObject

/**
 * A cloud recognizer: a session keeps the utterance's audio, and [transcribe]s it in one request
 * when it finishes, expecting the session's languages (all prepared ones in the automatic mode)
 * and the [vocabulary]. Whatever the service answers is one reading, without the full stop, ! or ?
 * services end a sentence with: the text often goes into a search field.
 */
internal abstract class CloudRecognizer : CloudSpeechEngine {
    @Volatile private var key: String? = null

    @Volatile protected var vocabulary: List<String> = emptyList()
        private set

    @Volatile override var languages: List<String> = emptyList()
        protected set

    override fun useKey(key: String?) {
        this.key = key?.takeIf { it.isNotBlank() }
    }

    override fun useVocabulary(words: List<String>) {
        vocabulary = words
    }

    /** Ready with a key; the languages only tell the service what to expect. */
    override fun prepare(languages: List<String>): Boolean {
        this.languages = if (key == null) emptyList() else languages
        return this.languages.isNotEmpty()
    }

    override fun newSession(languages: List<String>): SpeechSession {
        val key = key ?: throw SpeechUnavailable(Reason.NoKey, "no API key")
        val expected = languages.ifEmpty { this.languages }
        return object : SpeechSession {
            private val audio = ByteArrayOutputStream()

            override fun accept(pcm: ByteArray, length: Int) = audio.write(pcm, 0, length)

            override fun partial(): String = ""

            override fun finish(): List<Hypothesis> {
                // A tap, not a phrase: not worth a request, and services make things up on silence.
                if (Pcm.durationMillis(audio.size().toLong()) < Pcm.TAP_MILLIS) return emptyList()
                val text = transcribe(audio.toByteArray(), expected, key).trimEnd('.', '!', '?', '…').trimEnd()
                return listOf(Hypothesis(languageOf(text, expected), text, 1.0))
            }

            override fun close() = Unit
        }
    }

    /** The text of 16 kHz mono 16-bit [pcm], spoken in one of [languages]; blocking. */
    protected abstract fun transcribe(pcm: ByteArray, languages: List<String>, key: String): String
}

/**
 * Which of [expected] [text] is in, as far as its script tells: Cyrillic or Latin letters pick the
 * first expected language written so; otherwise the first expected one.
 */
internal fun languageOf(text: String, expected: List<String>): String {
    val cyrillic = text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.CYRILLIC }
    val latin = text.any { it in 'a'..'z' || it in 'A'..'Z' }
    val written = when {
        cyrillic -> expected.firstOrNull { it in CYRILLIC_LANGUAGES }
        latin -> expected.firstOrNull { it !in CYRILLIC_LANGUAGES }
        else -> null
    }
    return written ?: expected.firstOrNull().orEmpty()
}

/**
 * POSTs [body] to [url] and returns the response. A service that gave no answer, or answered
 * with an error status, is a [SpeechUnavailable] naming the status and the service's own error
 * code, for the log.
 */
internal fun post(url: String, headers: Map<String, String>, contentType: String, body: ByteArray): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", contentType)
        headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        connection.setFixedLengthStreamingMode(body.size)
        val (status, answer) = reaching {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in HTTP_OK_RANGE) connection.inputStream else connection.errorStream
            status to stream?.use { it.readBytes().decodeToString() }.orEmpty()
        }
        if (status !in HTTP_OK_RANGE) throw failure(status, answer)
        return answer
    } finally {
        connection.disconnect()
    }
}

/**
 * What an error status is to the user. The answer itself is quoted only for the service's own
 * errors: services quote a refused key back in part.
 */
private fun failure(status: Int, answer: String): IOException {
    val code = errorCode(answer)
    val told = listOfNotNull("HTTP $status", code).joinToString(" ")
    val reason = when {
        status == HTTP_UNAUTHORIZED -> Reason.KeyRefused

        status == HTTP_FORBIDDEN && code == OTHER_COUNTRY -> Reason.Region

        status == HTTP_FORBIDDEN -> Reason.NotAllowed

        // OpenAI answers "too many requests" to an account with no funds as well; its code tells the two apart.
        status == HTTP_PAYMENT_REQUIRED || (status == HTTP_TOO_MANY_REQUESTS && code in NOT_PAID) -> Reason.NotPaid

        status == HTTP_TOO_MANY_REQUESTS -> Reason.Busy

        else -> return SpeechUnavailable(Reason.ServiceError, "$told: ${excerpt(answer)}", status = status)
    }
    return SpeechUnavailable(reason, told, status = status)
}

/** The service's own name for the error: OpenAI's `error.code` (or `type`), Yandex's `error_code`. */
private fun errorCode(answer: String): String? = try {
    val json = JSONObject(answer)
    val error = json.optJSONObject("error")
    listOf(error?.optString("code"), error?.optString("type"), json.optString("error_code"))
        .firstOrNull { !it.isNullOrEmpty() && it != "null" }
} catch (_: JSONException) {
    null
}

/**
 * Runs [request]; a failure before the service answered (no route, a connection cut or refused, no
 * answer in time) is [Reason.NoNetwork], whatever the cause: it is named for the log.
 */
private inline fun <T> reaching(request: () -> T): T = try {
    request()
} catch (e: IOException) {
    throw SpeechUnavailable(Reason.NoNetwork, e.toString(), e)
}

/** The [name] text in a service's JSON [answer]; an [IOException] quoting the answer without one. */
internal fun answerText(answer: String, name: String): String = try {
    JSONObject(answer).getString(name).trim()
} catch (e: JSONException) {
    throw IOException("unexpected answer: ${excerpt(answer)}", e)
}

private fun excerpt(answer: String) = answer.replace(WHITESPACE, " ").take(EXCERPT_CHARS)

private const val CONNECT_TIMEOUT_MILLIS = 5_000
private const val READ_TIMEOUT_MILLIS = 20_000
private const val EXCERPT_CHARS = 200
private val HTTP_OK_RANGE = 200..299
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_PAYMENT_REQUIRED = 402
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429
private const val OTHER_COUNTRY = "unsupported_country_region_territory"
private val NOT_PAID = setOf("insufficient_quota", "credit_balance_exhausted", "billing_not_active")
private val WHITESPACE = Regex("""\s+""")
