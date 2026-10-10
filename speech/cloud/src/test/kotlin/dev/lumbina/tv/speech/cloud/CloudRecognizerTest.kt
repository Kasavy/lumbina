package dev.lumbina.tv.speech.cloud

import com.sun.net.httpserver.HttpServer
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable.Reason
import java.net.InetSocketAddress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudRecognizerTest {

    /** What the stand-in service got. */
    private class Received(val path: String, val query: String?, val headers: Map<String, String>, val body: ByteArray)

    private var received: Received? = null
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    private fun answer(status: Int, body: String): String {
        server.createContext("/") { exchange ->
            received = Received(
                path = exchange.requestURI.path,
                query = exchange.requestURI.rawQuery,
                headers = exchange.requestHeaders.mapValues { it.value.first() }.mapKeys { it.key.lowercase() },
                body = exchange.requestBody.readBytes(),
            )
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return "http://127.0.0.1:${server.address.port}/stt"
    }

    @After
    fun stop() {
        server.stop(0)
    }

    private val speech = ByteArray(32_000) { (it % 7).toByte() }

    private fun CloudRecognizer.recognize(languages: List<String>): String {
        useKey("secret-key")
        assertTrue(prepare(languages))
        return newSession(languages).run {
            accept(speech, speech.size)
            finish().single().text
        }
    }

    @Test
    fun `OpenAI gets the utterance as WAV, told the languages and the apps to expect`() {
        val url = answer(200, """{"text":" Открой настройки! "}""")
        val openAi = OpenAiRecognizer(url).apply { useVocabulary(listOf("RUTUBE", "聚好看", "Кинопоиск")) }

        assertEquals("Открой настройки", openAi.recognize(listOf("ru", "en")))

        val request = received!!
        assertEquals("Bearer secret-key", request.headers["authorization"])
        val body = request.body.decodeToString()
        assertTrue(body.contains("gpt-4o-mini-transcribe"))
        assertTrue(body.contains("Speech in Russian or English. Apps: RUTUBE, Кинопоиск."))
        // The language is left to the service: a fixed one garbles speech in another.
        assertFalse(body.contains("name=\"language\""))
        assertTrue(body.contains("RIFF") && body.contains("WAVEfmt "))
    }

    @Test
    fun `the prompt answered back on near silence counts as nothing heard`() {
        val url = answer(200, """{"text":"Speech in Russian or English. Apps: RUTUBE."}""")
        val openAi = OpenAiRecognizer(url).apply { useVocabulary(listOf("таймер", "Calendar", "RUTUBE", "VLC")) }
        assertEquals("", openAi.recognize(listOf("ru", "en")))
    }

    @Test
    fun `the vocabulary read back in part counts as nothing heard`() {
        val url = answer(200, """{"text":"таймер, Calendar, VLC,"}""")
        val openAi = OpenAiRecognizer(url).apply { useVocabulary(listOf("таймер", "Calendar", "RUTUBE", "VLC")) }
        assertEquals("", openAi.recognize(listOf("ru", "en")))
    }

    @Test
    fun `a tap is not sent at all`() {
        val url = answer(200, """{"text":"anything"}""")
        val openAi = OpenAiRecognizer(url).apply { useKey("secret-key") }
        assertTrue(openAi.prepare(listOf("ru")))
        val readings = openAi.newSession(listOf("ru")).run {
            accept(ByteArray(6_400), 6_400)
            finish()
        }
        assertTrue(readings.isEmpty())
        assertEquals(null, received)
    }

    @Test
    fun `the language of a reading is told by its script`() {
        assertEquals("ru", languageOf("Открой настройки", listOf("en", "ru")))
        assertEquals("en", languageOf("open settings", listOf("ru", "en")))
        assertEquals("ru", languageOf("42", listOf("ru", "en")))
    }

    @Test
    fun `Yandex gets the raw PCM and the language as it names it`() {
        val url = answer(200, """{"result":"погода"}""")

        assertEquals("погода", YandexRecognizer(url).recognize(listOf("xx", "en")))

        val request = received!!
        assertEquals("Api-Key secret-key", request.headers["authorization"])
        assertEquals("lang=en-US&format=lpcm&sampleRateHertz=16000", request.query)
        assertTrue(request.body.contentEquals(speech))
    }

    @Test
    fun `a session in one language tells Yandex that one`() {
        val url = answer(200, """{"result":"hello"}""")
        val yandex = YandexRecognizer(url).apply { useKey("secret-key") }
        assertTrue(yandex.prepare(listOf("ru", "en")))

        val text = yandex.newSession(listOf("en")).run {
            accept(speech, speech.size)
            finish().single().text
        }

        assertEquals("hello", text)
        assertEquals("lang=en-US&format=lpcm&sampleRateHertz=16000", received!!.query)
    }

    @Test
    fun `an error status fails the utterance without giving the key away`() {
        val url = answer(401, """{"error":{"message":"Incorrect API key provided: secr*****-key"}}""")

        val failure = runCatching { OpenAiRecognizer(url).recognize(listOf("ru")) }.exceptionOrNull()

        assertEquals(Reason.KeyRefused, (failure as SpeechUnavailable).reason)
        assertTrue(failure.message!!.contains("401"))
        assertFalse(failure.message!!.contains("secr"))
    }

    @Test
    fun `an account with nothing to pay with is told as such`() {
        val url = answer(429, """{"error":{"message":"You exceeded your current quota","code":"insufficient_quota"}}""")
        val failure = runCatching { OpenAiRecognizer(url).recognize(listOf("ru")) }.exceptionOrNull()
        assertEquals(Reason.NotPaid, (failure as SpeechUnavailable).reason)
    }

    @Test
    fun `a key the service takes and does not let recognize is told apart from a wrong one`() {
        val url = answer(403, """{"error_code":"PERMISSION_DENIED","error_message":"Permission denied"}""")
        val failure = runCatching { YandexRecognizer(url).recognize(listOf("ru")) }.exceptionOrNull()
        assertEquals(Reason.NotAllowed, (failure as SpeechUnavailable).reason)
        assertEquals("HTTP 403 PERMISSION_DENIED", failure.message)
    }

    @Test
    fun `a country the service does not serve is told as such`() {
        val url =
            answer(
                403,
                """{"error":{"message":"Country not supported","code":"unsupported_country_region_territory"}}""",
            )
        val failure = runCatching { OpenAiRecognizer(url).recognize(listOf("ru")) }.exceptionOrNull()
        assertEquals(Reason.Region, (failure as SpeechUnavailable).reason)
    }

    @Test
    fun `asking too often is told apart from an account with nothing to pay with`() {
        val url = answer(429, """{"error":{"message":"Rate limit reached","code":"rate_limit_exceeded"}}""")
        val failure = runCatching { OpenAiRecognizer(url).recognize(listOf("ru")) }.exceptionOrNull()
        assertEquals(Reason.Busy, (failure as SpeechUnavailable).reason)
    }

    @Test
    fun `a service that can't be reached is told as such`() {
        val port = server.address.port
        server.stop(0)

        val failure = runCatching { OpenAiRecognizer("http://127.0.0.1:$port/stt").recognize(listOf("ru")) }

        assertEquals(Reason.NoNetwork, (failure.exceptionOrNull() as SpeechUnavailable).reason)
    }

    @Test
    fun `a service that fails is told with the status it answered`() {
        val failure = runCatching { OpenAiRecognizer(answer(500, "oops")).recognize(listOf("ru")) }.exceptionOrNull()
        assertEquals(Reason.ServiceError, (failure as SpeechUnavailable).reason)
        assertEquals(500, failure.status)
        assertTrue(failure.message!!.contains("500: oops"))
    }

    @Test
    fun `a key that went missing is told as such`() {
        val recognizer = OpenAiRecognizer("http://127.0.0.1:1/")
        val failure = runCatching { recognizer.newSession(listOf("ru")) }.exceptionOrNull()
        assertEquals(Reason.NoKey, (failure as SpeechUnavailable).reason)
    }

    @Test
    fun `without a key nothing is ready`() {
        val recognizer = OpenAiRecognizer("http://127.0.0.1:1/")
        assertFalse(recognizer.prepare(listOf("ru")))
        assertTrue(recognizer.languages.isEmpty())
    }
}
