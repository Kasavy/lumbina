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
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
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
                if (Pcm.durationMillis(audio.size().toLong()) < MIN_AUDIO_MILLIS) return emptyList()
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
 * POSTs [body] to [url] and returns the response. A failure or an error status is an
 * [IOException] naming the status and the start of the service's answer; not for a refused key,
 * which services quote back in part. A service that can't be reached, or refuses the key, is a
 * [SpeechUnavailable].
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
        return reaching {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status !in HTTP_OK_RANGE) throw failure(status, connection)
            connection.inputStream.use { it.readBytes().decodeToString() }
        }
    } finally {
        connection.disconnect()
    }
}

private fun failure(status: Int, connection: HttpURLConnection): IOException {
    if (status in REFUSED) return SpeechUnavailable(Reason.KeyRefused, "HTTP $status: the key was refused")
    val answer = connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
    return IOException("HTTP $status: ${excerpt(answer)}")
}

/** Runs [request]; no route to the service, or no answer in time, is [Reason.NoNetwork]. */
private inline fun <T> reaching(request: () -> T): T = try {
    request()
} catch (e: IOException) {
    val unreachable = e is UnknownHostException || e is ConnectException || e is SocketTimeoutException
    throw if (unreachable) SpeechUnavailable(Reason.NoNetwork, e.toString(), e) else e
}

/** The [name] text in a service's JSON [answer]; an [IOException] quoting the answer without one. */
internal fun answerText(answer: String, name: String): String = try {
    JSONObject(answer).getString(name).trim()
} catch (e: JSONException) {
    throw IOException("unexpected answer: ${excerpt(answer)}", e)
}

private fun excerpt(answer: String) = answer.replace(WHITESPACE, " ").take(EXCERPT_CHARS)

private const val MIN_AUDIO_MILLIS = 500
private const val CONNECT_TIMEOUT_MILLIS = 5_000
private const val READ_TIMEOUT_MILLIS = 20_000
private const val EXCERPT_CHARS = 200
private val HTTP_OK_RANGE = 200..299
private val REFUSED = setOf(401, 403)
private val WHITESPACE = Regex("""\s+""")
