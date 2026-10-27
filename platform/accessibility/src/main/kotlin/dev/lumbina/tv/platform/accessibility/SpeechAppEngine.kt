package dev.lumbina.tv.platform.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.Hypothesis
import dev.lumbina.tv.core.domain.speech.Pcm
import dev.lumbina.tv.core.domain.speech.SpeechApp
import dev.lumbina.tv.core.domain.speech.SpeechApps
import dev.lumbina.tv.core.domain.speech.SpeechEngine
import dev.lumbina.tv.core.domain.speech.SpeechSession
import dev.lumbina.tv.core.domain.speech.SpeechUnavailable
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

/**
 * Recognition by another app on the device, one that serves speech to apps (Android's
 * RecognitionService). Such an app records by itself, from Android's microphone input, which has no
 * sound where the remote's mic is the firmware's own: so a session asks the app to listen and sends
 * the remote's audio into its recording, through the helper that feeds apps ([MicInjection]).
 *
 * The app is told the language to expect and may not heed it. It also ends a phrase by itself when
 * the speech pauses: it is then asked to listen again for as long as the key is held, and what it
 * heard each time makes the phrase.
 *
 * An app let go of may record on for some seconds, and asked to listen meanwhile it answers that it
 * is busy and starts recording anew: asking again only keeps it busy. So it is never asked while the
 * helper says it records; it is waited for, briefly, and then the user is told to wait.
 */
@Singleton
class SpeechAppEngine @Inject internal constructor(
    @ApplicationContext private val context: Context,
    private val apps: SpeechApps,
    private val log: LumbinaLog,
) : SpeechEngine {

    /** The way into apps' recordings, for as long as the remote's service runs on a device that has one. */
    @Volatile internal var feed: MicInjection.Speech? = null

    @Volatile private var app: SpeechApp? = null

    /** The app chosen by the last [prepare], to be fed by a helper starting now; null before one. */
    internal val chosen: String? get() = app?.packageName

    @Volatile override var languages: List<String> = emptyList()
        private set

    /** When the session closed last will have let go of the app. */
    @Volatile private var freeAt = 0L

    /** The apps seen to answer in a settled and a changing part ([inParts]): they give no answer to a stop. */
    private val answeringInParts = ConcurrentHashMap.newKeySet<String>()

    /** Ready with an app to ask; the languages only tell it what to expect. */
    override fun prepare(languages: List<String>): Boolean {
        val found = runBlocking { apps.installed() }.firstOrNull()
        app = found
        this.languages = if (found == null) emptyList() else languages
        found?.let { feed?.alsoFeed(it.packageName) }
        return found != null
    }

    override fun newSession(languages: List<String>): SpeechSession {
        val app = checkNotNull(app) { "no app to recognize speech" }
        val feed = feed ?: throw unfed(app)
        // A helper that is starting (to take this app in, or for any other reason) is waited for;
        // with none on its way there is nothing to wait for.
        feed.alsoFeed(app.packageName)
        val deadline = SystemClock.elapsedRealtime() + FEED_MILLIS
        while (feed.starting && SystemClock.elapsedRealtime() < deadline) Thread.sleep(POLL_MILLIS)
        val uid = feed.uidFed(app.packageName) ?: throw unfed(app)
        // The session before is still letting go of the app: Android gives one caller one listening at a time.
        val closing = freeAt - SystemClock.elapsedRealtime()
        if (closing > 0) Thread.sleep(closing)
        return Session(app, uid, feed, languages.ifEmpty { this.languages }).apply { open() }
    }

    /** Waits until the app records no more, at most [millis]; false if it still does. */
    private fun awaitIdle(feed: MicInjection.Speech, uid: Int, millis: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + millis
        while (feed.records(uid)) {
            if (SystemClock.elapsedRealtime() >= deadline) return false
            Thread.sleep(POLL_MILLIS)
        }
        return true
    }

    private fun occupied(app: SpeechApp) =
        SpeechUnavailable(SpeechUnavailable.Reason.Occupied, "${app.packageName} is still at the phrase before")

    private fun unfed(app: SpeechApp) =
        SpeechUnavailable(SpeechUnavailable.Reason.MicNotGiven, "the remote's mic can't be given to ${app.packageName}")

    private fun request(expected: List<String>) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        .apply {
            expected.firstOrNull()?.let {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(it).toLanguageTag())
            }
        }

    private fun failure(app: SpeechApp, error: Int): IOException = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            SpeechUnavailable(SpeechUnavailable.Reason.NoNetwork, "${app.packageName}: error $error")

        // The app's own service behind it failed: said as that, not as a phrase that was not made out.
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechUnavailable(
            SpeechUnavailable.Reason.ServiceError,
            "${app.packageName}: recognizer error $error",
            status = error,
        )

        else -> IOException("${app.packageName}: recognizer error $error")
    }

    private inner class Session(
        private val app: SpeechApp,
        private val uid: Int,
        private val feed: MicInjection.Speech,
        private val expected: List<String>,
    ) : SpeechSession {
        private val main = Handler(Looper.getMainLooper())
        private val owned = feed.own(uid)

        @Volatile private var answers = newAnswers()
        private var recognizer: SpeechRecognizer? = null // main thread only
        private var listeningSince = 0L // main thread only

        /** The key is up: the app ending a round now is not asked to listen again. */
        @Volatile private var over = false

        private fun newAnswers() = Answers(app.packageName in answeringInParts, isHeld = { !over }, again = ::listen)
        private var sentBytes = 0L
        private var sendingSince = 0L

        /** How much was sent when the last sound loud enough to be speech went. */
        private var spokenBytes = 0L

        /** Has the app listening; an app that refuses at once fails the session here, not after the phrase. */
        fun open() {
            if (!awaitIdle(feed, uid, LEFTOVER_MILLIS)) {
                log.log(TAG, "${app.packageName} still records from before: not asked")
                close()
                throw occupied(app)
            }
            main.post(connect)
            awaitListening()
            // Still busy with a listening left over from before. It is let go of and asked once
            // more only when it records no longer, through a new connection: the one that was
            // refused stays refused. Asked while it records, it would start recording anew.
            if (answers.error in BUSY) {
                log.log(TAG, "${app.packageName} is busy (error ${answers.error}): letting it finish")
                main.post {
                    recognizer?.destroy()
                    recognizer = null
                }
                Thread.sleep(BUSY_MILLIS)
                if (awaitIdle(feed, uid, BUSY_IDLE_MILLIS)) {
                    answers = newAnswers()
                    main.post(connect)
                    awaitListening()
                }
            }
            answers.error?.let { error ->
                close()
                throw if (error in BUSY) occupied(app) else failure(app, error)
            }
        }

        /** A new connection to the app, asked to listen (main thread). */
        private val connect = Runnable {
            val service = ComponentName.unflattenFromString(app.service)
            recognizer = SpeechRecognizer.createSpeechRecognizer(context, service).apply {
                setRecognitionListener(answers)
            }
            listen()
        }

        /** What is said meanwhile waits with the caller; a slow start costs the phrase's beginning. */
        private fun awaitListening() {
            if (!answers.ready.await(READY_MILLIS, TimeUnit.MILLISECONDS)) {
                log.log(TAG, "${app.packageName} is not listening after $READY_MILLIS ms")
            }
        }

        /** Main thread. */
        private fun listen() {
            listeningSince = SystemClock.elapsedRealtime()
            recognizer?.startListening(request(expected))
        }

        override fun accept(pcm: ByteArray, length: Int) {
            if (sentBytes == 0L) sendingSince = SystemClock.elapsedRealtime()
            if (!feed.send(pcm, length)) return
            sentBytes += length
            if ((Pcm.levelDb(pcm, length) ?: SILENT_DB) >= SPEECH_DB) spokenBytes = sentBytes
        }

        override fun partial(): String = answers.text

        override fun released() {
            over = true
        }

        override fun finish(): List<Hypothesis> {
            over = true
            // A tap, not a phrase: nothing worth waiting for.
            if (Pcm.durationMillis(sentBytes) < Pcm.TAP_MILLIS && answers.text.isEmpty()) return emptyList()
            // The helper plays what it gets at the pace of sound: what came in a burst is still on its way.
            val behind = Pcm.durationMillis(sentBytes) - (SystemClock.elapsedRealtime() - sendingSince)
            answers.ended.await(behind.coerceIn(0, BEHIND_MAX_MILLIS) + TAIL_MILLIS, TimeUnit.MILLISECONDS)
            awaitSettled(SystemClock.elapsedRealtime())
            if (answers.inParts) answeringInParts += app.packageName
            // By the book an app told to stop answers with what it heard; one that answers in parts doesn't.
            if (!answers.isEnded && !answers.inParts) {
                main.post { recognizer?.stopListening() }
                answers.ended.await(STOPPED_MILLIS, TimeUnit.MILLISECONDS)
            }
            return answer()
        }

        /**
         * Waits for what the app heard to have stopped changing: an app that answers in parts
         * gives no answer to a stop, and left alone answers only once the silence the helper plays
         * has lasted, well after its text is there. Text that has stood unchanged since before the
         * phrase ended ([since]) is taken a moment after it, but no sooner than the app has had
         * [LAST_WORD_MILLIS] since it was played the last speech: a word said right before the key
         * went up shows in the text that much later. The first words of a short phrase may come
         * only after it has ended, so they are waited for, whichever app it is.
         */
        private fun awaitSettled(since: Long) {
            val deadline = since + SETTLE_MAX_MILLIS
            // The helper plays at the pace of sound, so where a sound is in the audio is when the app hears it.
            val lastWordBy = sendingSince + Pcm.durationMillis(spokenBytes) + LAST_WORD_MILLIS
            while (!answers.isEnded && SystemClock.elapsedRealtime() < deadline) {
                val now = SystemClock.elapsedRealtime()
                val done = when {
                    answers.text.isEmpty() -> now - since >= FIRST_MILLIS

                    !answers.inParts -> return

                    else -> {
                        val enough = if (answers.settled) SETTLED_MILLIS else UNSETTLED_MILLIS
                        now - answers.changedAt >= enough && now >= maxOf(since + AFTER_END_MILLIS, lastWordBy)
                    }
                }
                if (done) return
                answers.ended.await(POLL_MILLIS, TimeUnit.MILLISECONDS)
            }
        }

        /** What the app heard, if anything: its failure counts only when it heard nothing. */
        private fun answer(): List<Hypothesis> {
            val text = answers.text
            if (text.isNotEmpty()) return listOf(Hypothesis(expected.firstOrNull().orEmpty(), text, 1.0))
            return answers.error?.let { throw failure(app, it) } ?: emptyList()
        }

        /**
         * Stopped, then let go of, no sooner than [STARTED_MILLIS] after the app was last asked
         * to listen: one cancelled while starting may go on recording and answer the next ones
         * that it is busy, and a stop ends a recording where a cancel alone does not.
         */
        override fun close() {
            // Closed without a finish too (a tap, no speech): a round ending now starts no other.
            over = true
            freeAt = SystemClock.elapsedRealtime() + STARTED_MILLIS + STOPPING_MILLIS
            main.post {
                val wait = STARTED_MILLIS - (SystemClock.elapsedRealtime() - listeningSince)
                freeAt = SystemClock.elapsedRealtime() + wait.coerceAtLeast(0) + STOPPING_MILLIS + FREED_MILLIS
                main.postDelayed({ recognizer?.stopListening() }, wait.coerceAtLeast(0))
                main.postDelayed(
                    {
                        recognizer?.destroy()
                        recognizer = null
                        // Its recordings are the session's until it records no more.
                        feed.release(owned)
                    },
                    wait.coerceAtLeast(0) + STOPPING_MILLIS,
                )
            }
        }
    }

    private companion object {
        const val TAG = "voice"
        const val POLL_MILLIS = 50L

        /** How long the helper has to start again with a new app. */
        const val FEED_MILLIS = 3_000L
        const val READY_MILLIS = 3_000L
        const val BEHIND_MAX_MILLIS = 2_500L

        /** For the last frames to reach the app before the phrase is taken for ended. */
        const val TAIL_MILLIS = 300L

        /** How long an app told to stop has to answer. */
        const val STOPPED_MILLIS = 2_000L

        /** How long the first words of a phrase that has ended are waited for. */
        const val FIRST_MILLIS = 1_500L

        /** How long what was heard must stay unchanged to be taken for the answer: settled, or still changing. */
        const val SETTLED_MILLIS = 500L
        const val UNSETTLED_MILLIS = 700L
        const val AFTER_END_MILLIS = 100L

        /** How long after a word is played to the app it may take to show in its text. */
        const val LAST_WORD_MILLIS = 1_000L

        /** A chunk this loud may be speech; the remote's own noise stays below. */
        const val SPEECH_DB = -30.0
        const val SILENT_DB = -100.0
        const val SETTLE_MAX_MILLIS = 3_000L

        /** How long an app is left to start listening before it may be cancelled. */
        const val STARTED_MILLIS = 1_000L
        const val BUSY_MILLIS = 500L

        /** How long a new phrase waits for the app to end a recording left over from before. */
        const val LEFTOVER_MILLIS = 2_000L

        /** How long an app that answered "busy" is given to end that recording before it is asked again. */
        const val BUSY_IDLE_MILLIS = 3_000L
        const val STOPPING_MILLIS = 300L

        /** For Android to see the connection gone, after it is let go of. */
        const val FREED_MILLIS = 100L

        /** Busy, or refused as busy a moment ago: a connection that was is "disconnected" when asked again. */
        val BUSY = setOf(
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        )
    }
}

/**
 * What the app answers, as it comes (main thread), for the session's threads to read. An app ends a
 * round of listening by itself when the speech pauses: while the key [isHeld] it is asked to listen
 * [again], and the rounds' texts are joined.
 */
internal class Answers(
    startInParts: Boolean,
    private val isHeld: () -> Boolean,
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val again: () -> Unit,
) : RecognitionListener {
    val ready = CountDownLatch(1)

    /** The app answers no more: its last round ended, or it failed. */
    val ended = CountDownLatch(1)

    /** The rounds that ended, and the one going on: one value, so a reader never sees a round twice. */
    @Volatile private var heard = "" to ""

    /** Everything heard so far. */
    val text: String get() = heard.let { (said, hearing) -> joined(said, hearing) }

    /** The readings so far come as a settled part and a changing one ([inParts]). */
    @Volatile var inParts = startInParts
        private set

    /** Nothing of what was heard so far is still changing. */
    @Volatile var settled = false
        private set

    /** When what was heard so far last changed. */
    @Volatile var changedAt = 0L
        private set

    /** The failure that ended the listening; hearing nothing is none. */
    @Volatile var error: Int? = null
        private set

    val isEnded: Boolean get() = ended.count == 0L

    private var roundSince = now()
    private var rounds = 1

    override fun onReadyForSpeech(params: Bundle?) = ready.countDown()

    override fun onBeginningOfSpeech() = ready.countDown()

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onPartialResults(partialResults: Bundle?) {
        val readings = partialResults.readings()
        if (inParts(readings)) inParts = true
        val hearing = soFar(readings, inParts)
        if (hearing != heard.second) changedAt = now()
        heard = heard.first to hearing
        settled = inParts && readings.size == 2 && readings[1].isBlank() && hearing.isNotEmpty()
    }

    override fun onResults(results: Bundle?) = roundEnded(results.readings().firstOrNull().orEmpty(), failed = null)

    override fun onError(error: Int) {
        val heardNothing = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        roundEnded(result = "", failed = error.takeUnless { heardNothing })
    }

    /**
     * A round's text is its result, or what it heard last if the result is empty. Another round
     * follows while the key is held; not after a failure, nor after rounds that end as soon as
     * they start (an app that can't listen at all would be asked without end), nor after a round
     * that heard nothing: nobody is speaking, and an app asked again right after a silence may fail
     * and stay busy.
     */
    private fun roundEnded(result: String, failed: Int?) {
        val now = now()
        val round = result.trim().ifEmpty { heard.second }
        heard = heard.let { (said, _) -> joined(said, round) to "" }
        settled = false
        changedAt = now
        val worthAnother = round.isNotEmpty() && now - roundSince >= MIN_ROUND_MILLIS && rounds < MAX_ROUNDS
        if (failed == null && isHeld() && worthAnother) {
            rounds++
            roundSince = now
            again()
        } else {
            error = failed
            ended.countDown()
        }
        // After the error is set: who waits for the app to listen reads it next.
        ready.countDown()
    }

    companion object {
        const val MIN_ROUND_MILLIS = 300L
        const val MAX_ROUNDS = 20
    }
}

private fun Bundle?.readings(): List<String> = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()

private fun joined(first: String, second: String): String = listOf(first, second).filter {
    it.isNotEmpty()
}.joinToString(" ")

/**
 * The text heard so far out of a recognizer's partial readings. They are alternatives by the
 * book, the likeliest first; Google's TV search gives the settled part and the part still changing
 * instead (`["Open", " Smart T"]`). [parts]: this app is known to answer so.
 */
internal fun soFar(readings: List<String>, parts: Boolean = inParts(readings)): String =
    (if (parts && readings.size == 2) readings.joinToString("") else readings.firstOrNull().orEmpty()).trim()

/**
 * Whether partial readings are a settled part and a changing part rather than alternatives: told
 * by the space kept between them.
 */
internal fun inParts(readings: List<String>): Boolean =
    readings.size == 2 && (readings[0].isEmpty() || readings[1].isEmpty() || readings[1].startsWith(" "))
