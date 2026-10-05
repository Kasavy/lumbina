package dev.lumbina.tv.speech.cloud

import dev.lumbina.tv.core.domain.speech.Pcm
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Yandex SpeechKit's short-audio recognition (up to 30 s): the raw PCM, with a service account's
 * API key. The service takes one language: the first expected one it knows, else Russian.
 */
@Singleton
internal class YandexRecognizer(private val endpoint: String) : CloudRecognizer() {

    @Inject constructor() : this(ENDPOINT)

    override fun transcribe(pcm: ByteArray, languages: List<String>, key: String): String {
        val lang = languages.firstNotNullOfOrNull(LOCALES::get) ?: LOCALES.getValue("ru")
        val query = "lang=${URLEncoder.encode(lang, "UTF-8")}&format=lpcm&sampleRateHertz=${Pcm.SAMPLE_RATE}"
        val answer = post(
            url = "$endpoint?$query",
            headers = mapOf("Authorization" to "Api-Key $key"),
            contentType = "application/octet-stream",
            body = pcm,
        )
        return answerText(answer, "result")
    }

    private companion object {
        const val ENDPOINT = "https://stt.api.cloud.yandex.net/speech/v1/stt:recognize"

        /** The languages the service recognizes, by the codes Lumbina uses. */
        val LOCALES = mapOf(
            "ru" to "ru-RU",
            "en" to "en-US",
            "de" to "de-DE",
            "es" to "es-ES",
            "fr" to "fr-FR",
            "it" to "it-IT",
            "kk" to "kk-KZ",
            "nl" to "nl-NL",
            "pl" to "pl-PL",
            "pt" to "pt-PT",
            "sv" to "sv-SE",
            "tr" to "tr-TR",
            "uz" to "uz-UZ",
            "fi" to "fi-FI",
            "he" to "he-IL",
        )
    }
}
