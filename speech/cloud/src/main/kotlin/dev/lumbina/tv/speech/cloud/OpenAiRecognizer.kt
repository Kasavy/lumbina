package dev.lumbina.tv.speech.cloud

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI's transcription API with gpt-4o-mini-transcribe: the utterance as a WAV file. The
 * service tells the language itself; a prompt names the languages to expect (else it drifts into
 * others on short phrases) and the app names in those scripts. On near silence it may answer with
 * the prompt, or words of it, which then count as nothing heard.
 */
@Singleton
internal class OpenAiRecognizer(private val endpoint: String) : CloudRecognizer() {

    @Inject constructor() : this(ENDPOINT)

    override fun transcribe(pcm: ByteArray, languages: List<String>, key: String): String {
        val boundary = "lumbina-" + UUID.randomUUID()
        val prompt = prompt(languages)
        val form = Multipart(boundary).apply {
            field("model", MODEL)
            field("response_format", "json")
            prompt?.let { field("prompt", it) }
            file("file", "speech.wav", "audio/wav", wav(pcm))
        }
        val answer = post(
            url = endpoint,
            headers = mapOf("Authorization" to "Bearer $key"),
            contentType = "multipart/form-data; boundary=$boundary",
            body = form.bytes(),
        )
        val text = answerText(answer, "text")
        return if (prompt != null && echoes(text, prompt)) "" else text
    }

    /**
     * [text] is (a good part of) [prompt] rather than speech: a piece of it, its start, or a list of
     * its words (the service skips some when it reads them back).
     */
    private fun echoes(text: String, prompt: String): Boolean {
        val said = text.trimEnd('.', ',').trim()
        if (said.length >= MIN_ECHO_CHARS && (prompt.contains(said) || said.contains(prompt.take(MIN_ECHO_CHARS)))) {
            return true
        }
        val listed = said.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return listed.size >= MIN_ECHO_WORDS && listed.all { prompt.contains(it, ignoreCase = true) }
    }

    private fun prompt(languages: List<String>): String? {
        val names = languages.map { code ->
            val locale = Locale.forLanguageTag(code)
            locale.getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotEmpty() } ?: code
        }
        val parts = listOfNotNull(
            names.takeIf { it.isNotEmpty() }?.let { "Speech in ${it.joinToString(" or ")}." },
            vocabulary.filter(::inLatinOrCyrillic).take(MAX_WORDS).takeIf { it.isNotEmpty() }
                ?.let { "Apps: ${it.joinToString(", ")}." },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")?.take(MAX_PROMPT_CHARS)
    }

    private class Multipart(private val boundary: String) {
        private val out = ByteArrayOutputStream()

        fun field(name: String, value: String) {
            part("""Content-Disposition: form-data; name="$name"""")
            out.write(value.toByteArray())
            out.write(CRLF)
        }

        fun file(name: String, fileName: String, type: String, content: ByteArray) {
            part("""Content-Disposition: form-data; name="$name"; filename="$fileName"""", "Content-Type: $type")
            out.write(content)
            out.write(CRLF)
        }

        fun bytes(): ByteArray = out.toByteArray() + "--$boundary--\r\n".toByteArray()

        private fun part(vararg headers: String) {
            out.write("--$boundary\r\n".toByteArray())
            headers.forEach { out.write("$it\r\n".toByteArray()) }
            out.write(CRLF)
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        const val MODEL = "gpt-4o-mini-transcribe"
        const val MAX_WORDS = 60
        const val MAX_PROMPT_CHARS = 1_000
        const val MIN_ECHO_CHARS = 12
        const val MIN_ECHO_WORDS = 3

        /** Letters of the Latin, Greek and Cyrillic blocks only: names the expected speech can say. */
        fun inLatinOrCyrillic(word: String): Boolean = word.none { it.isLetter() && it.code >= CYRILLIC_END }

        const val CYRILLIC_END = 0x0530
        val CRLF = "\r\n".toByteArray()
    }
}
