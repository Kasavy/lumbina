package dev.lumbina.tv.core.domain.speech

/**
 * Hands-free end of an utterance, where no button is held: an app asking for speech, a tapped mic key.
 * Listens until speech is followed by [silenceMillis] of quiet, gives up when no speech starts in
 * [noSpeechMillis], and stops after [maxMillis] in any case.
 *
 * Speech is audio louder than the quietest audio heard so far by [SPEECH_ABOVE_FLOOR_DB] (and than
 * [MIN_SPEECH_DB] at all), or simply loud ([LOUD_DB]) for when it starts at once, lasting at least
 * [minSpeechMillis] in a row, so the click of a key is not speech. All-zero audio (a remote idle
 * until a key is pressed) tells nothing and is skipped. The silence is counted in audio, which may
 * arrive in bursts; the time limits in wall time, since an idle remote sends its zeros slower than
 * real time.
 */
class EndOfSpeech(
    private val now: () -> Long,
    private val silenceMillis: Long = 1_000,
    private val noSpeechMillis: Long = 8_000,
    private val maxMillis: Long = 15_000,
    private val minSpeechMillis: Long = 160,
) {
    private val startedAt = now()
    private val lock = Any()
    private var floorDb = Double.MAX_VALUE
    private var speechMillis = 0L
    private var quietMillis = 0L

    /** Whether speech has started. */
    val heardSpeech: Boolean get() = synchronized(lock) { speechMillis >= minSpeechMillis }

    /** Takes [length] bytes of 16-bit samples just captured. */
    fun hear(pcm: ByteArray, length: Int) = synchronized(lock) {
        val db = Pcm.levelDb(pcm, length) ?: return
        val millis = Pcm.durationMillis(length.toLong())
        floorDb = minOf(floorDb, db)
        val loud = db >= LOUD_DB || (db >= MIN_SPEECH_DB && db >= floorDb + SPEECH_ABOVE_FLOOR_DB)
        when {
            loud -> {
                speechMillis += millis
                quietMillis = 0
            }

            speechMillis >= minSpeechMillis -> quietMillis += millis

            // Before speech started, a lone loud blip (a key click) is forgotten after a pause.
            else -> speechMillis = 0
        }
    }

    /** Whether to keep listening. */
    fun listening(): Boolean = synchronized(lock) {
        val elapsed = now() - startedAt
        when {
            elapsed >= maxMillis -> false
            speechMillis < minSpeechMillis -> elapsed < noSpeechMillis
            else -> quietMillis < silenceMillis
        }
    }

    private companion object {
        const val SPEECH_ABOVE_FLOOR_DB = 15.0
        const val MIN_SPEECH_DB = -50.0
        const val LOUD_DB = -30.0
    }
}
