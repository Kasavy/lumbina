package dev.lumbina.tv.platform.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.AudioTrack
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.os.Looper
import android.os.Process
import androidx.annotation.RequiresApi
import dev.lumbina.tv.core.domain.speech.Pcm
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * The helper of [MicInjection]: not part of Lumbina's processes, but run as the adb shell with
 * `app_process` from Lumbina's APK. The shell holds MODIFY_AUDIO_ROUTING, which lets it register
 * an audio policy that injects audio into the recordings of given apps, the way Android's virtual
 * devices give an app a microphone (proved on the Vidda projector).
 *
 * It connects back to Lumbina's socket and stays exactly as long as that connection: it tells
 * when one of the given apps starts or stops recording (`rec <uid> 1|0`) and plays what Lumbina
 * sends (the remote's 16 kHz mono 16-bit PCM) into the recordings going on. When another app
 * starts to record it says so (`heard <uid>`), for Lumbina to ask the user about it. Lumbina
 * stays the remote's only reader. The audio-policy classes are system API, hence the reflection.
 *
 * Usage: `MicInjector <Lumbina's abstract socket> [<uid>...]`; with no uid it only listens for
 * apps that record.
 */
@SuppressLint("PrivateApi", "DiscouragedPrivateApi", "SoonBlockedPrivateApi")
@RequiresApi(Build.VERSION_CODES.Q) // a recording's uid, for the shell
object MicInjector {
    private const val SAMPLE_RATE = Pcm.SAMPLE_RATE
    private const val SILENCE_BYTES = SAMPLE_RATE * Pcm.BYTES_PER_SAMPLE / 50 // 20 ms
    private const val READ_BYTES = Pcm.FRAME_BYTES
    private const val WATCH_MILLIS = 40L

    /** With no app to feed there is only the question to raise, and that can wait a moment. */
    private const val IDLE_WATCH_MILLIS = 250L
    private const val GO_MILLIS = 20_000

    /** What Lumbina says first to the helper it accepted. */
    const val GO = "go"

    /** At most ~2 s waits for a track: more means it stalled, and old audio is no use. */
    private const val MAX_QUEUED = 50

    // The helper's whole life: whatever fails, Lumbina hears why before it exits.
    @Suppress("TooGenericExceptionCaught")
    @JvmStatic
    fun main(args: Array<String>) {
        val uids = args.drop(1).map(String::toInt)
        val socket = LocalSocket()
        // Nobody to tell: Lumbina is gone already.
        runCatching { socket.connect(LocalSocketAddress(args[0], LocalSocketAddress.Namespace.ABSTRACT)) }
            .onFailure { exitProcess(1) }
        // Connected is not accepted: a helper Lumbina gave up on would otherwise register a second
        // policy for the same apps and feed them silence. Nothing is registered before Lumbina's "go".
        if (!awaitGo(socket)) exitProcess(1)
        val lumbina = Lumbina(socket.outputStream)
        val code = try {
            run(uids, socket.inputStream, lumbina)
            0
        } catch (e: Exception) {
            lumbina.say("log failed: $e")
            1
        }
        exitProcess(code) // the activity thread's threads would keep the process alive
    }

    /** Lumbina's first line, "go", within [GO_MILLIS]; read byte by byte, as audio follows it. */
    private fun awaitGo(socket: LocalSocket): Boolean = runCatching {
        socket.soTimeout = GO_MILLIS
        val line = StringBuilder()
        while (true) {
            val b = socket.inputStream.read()
            if (b < 0 || b == '\n'.code) break
            line.append(b.toChar())
        }
        socket.soTimeout = 0
        line.toString() == GO
    }.getOrDefault(false)

    private fun run(uids: List<Int>, input: InputStream, lumbina: Lumbina) {
        Looper.prepareMainLooper()
        val context = systemContext()
        val audio = context.getSystemService(AudioManager::class.java)
        if (uids.isEmpty()) {
            lumbina.say("log feeding no app yet")
            thread(isDaemon = true, name = "watch") { watch(audio, emptyList(), lumbina) }
            return receive(input, emptyList())
        }
        val policy = Policy(context, audio, uids)
        if (!policy.register()) return lumbina.say("log the audio policy was refused")
        try {
            val feeds = uids.map { uid ->
                Feed(uid, policy.track(uid)) { error ->
                    lumbina.say("log the track for $uid failed ($error)")
                    exitProcess(1)
                }
            }
            lumbina.say("log feeding the recordings of ${uids.joinToString()}")
            feeds.forEach { feed -> thread(isDaemon = true, name = "feed-${feed.uid}") { feed.play() } }
            thread(isDaemon = true, name = "watch") { watch(audio, feeds, lumbina) }
            receive(input, feeds)
        } finally {
            policy.unregister()
        }
    }

    /** Lumbina's audio, to every feed whose app records; returns when Lumbina hangs up. */
    private fun receive(input: InputStream, feeds: List<Feed>) {
        val buffer = ByteArray(READ_BYTES + 1)
        var carry = 0 // an odd byte: half a sample, waiting for the rest
        while (true) {
            val n = input.read(buffer, carry, READ_BYTES)
            if (n < 0) return
            val total = carry + n
            val even = total and 1.inv()
            if (even > 0) {
                val chunk = buffer.copyOf(even)
                feeds.filter { it.recording }.forEach { it.offer(chunk) }
            }
            carry = total - even
            if (carry == 1) buffer[0] = buffer[total - 1]
        }
    }

    private fun watch(audio: AudioManager, feeds: List<Feed>, lumbina: Lumbina) {
        val clientUid = AudioRecordingConfiguration::class.java.getMethod("getClientUid")
        val fed = feeds.map { it.uid }.toSet()
        var others = emptySet<Int>()
        while (true) {
            val recording = audio.activeRecordingConfigurations.map { clientUid.invoke(it) as Int }.toSet()
            // Apps of the user's, not the system's own recorders; each said once per recording.
            val strangers = recording.filter { it >= Process.FIRST_APPLICATION_UID && it !in fed }.toSet()
            (strangers - others).forEach { lumbina.say("heard $it") }
            others = strangers
            for (feed in feeds) {
                val now = feed.uid in recording
                if (now != feed.recording) {
                    feed.recording = now
                    if (!now) feed.queue.clear()
                    lumbina.say("rec ${feed.uid} ${if (now) 1 else 0}")
                }
            }
            Thread.sleep(if (feeds.isEmpty()) IDLE_WATCH_MILLIS else WATCH_MILLIS)
        }
    }

    /**
     * One app's injected microphone. Something is always written, silence when Lumbina sends
     * nothing: an app gives up on a recording that starts on a track with nothing playing.
     */
    private class Feed(val uid: Int, private val track: AudioTrack, private val onDead: (Int) -> Unit) {
        @Volatile var recording = false
        val queue = ConcurrentLinkedQueue<ByteArray>()
        private val silence = ByteArray(SILENCE_BYTES)

        fun offer(chunk: ByteArray) {
            queue.add(chunk)
            while (queue.size > MAX_QUEUED) queue.poll()
        }

        fun play() {
            track.play()
            while (true) {
                val chunk = queue.poll() ?: silence
                // A dead track (the audio server restarted) fails at once: no busy loop.
                val written = track.write(chunk, 0, chunk.size)
                if (written < 0) onDead(written)
            }
        }
    }

    /** An injecting mix per app, in one audio policy. */
    private class Policy(context: Context, private val audio: AudioManager, uids: List<Int>) {
        private val ruleClass = Class.forName("android.media.audiopolicy.AudioMixingRule")
        private val ruleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")
        private val mixClass = Class.forName("android.media.audiopolicy.AudioMix")
        private val mixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
        private val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
        private val policyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")

        private val mixes = uids.associateWith(::mix)
        private val policy = policyBuilderClass.getConstructor(Context::class.java).newInstance(context).let {
            mixes.values.forEach { mix -> policyBuilderClass.getMethod("addMix", mixClass).invoke(it, mix) }
            policyBuilderClass.getMethod("build").invoke(it)
        }

        fun register(): Boolean =
            AudioManager::class.java.getMethod("registerAudioPolicy", policyClass).invoke(audio, policy) == 0

        fun unregister() {
            AudioManager::class.java.getMethod("unregisterAudioPolicy", policyClass).invoke(audio, policy)
        }

        /**
         * The track that feeds [uid]'s recordings, sent to the mix's own device: otherwise the
         * firmware plays it through the speaker and the app records silence.
         */
        fun track(uid: Int): AudioTrack {
            val mix = mixes.getValue(uid)
            val track = policyClass.getMethod("createAudioTrackSource", mixClass).invoke(policy, mix) as AudioTrack
            val address = mixClass.getMethod("getRegistration").invoke(mix) as String
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.address == address }
                ?.let { device: AudioDeviceInfo -> track.setPreferredDevice(device) }
            return track
        }

        private fun mix(uid: Int): Any {
            val rule = ruleBuilderClass.getConstructor().newInstance().let { builder ->
                ruleBuilderClass.getMethod("setTargetMixRole", Int::class.java)
                    .invoke(builder, ruleClass.getField("MIX_ROLE_INJECTOR").getInt(null))
                ruleBuilderClass.getMethod("addMixRule", Int::class.java, Any::class.java)
                    .invoke(builder, ruleClass.getField("RULE_MATCH_UID").getInt(null), uid)
                ruleBuilderClass.getMethod("build").invoke(builder)
            }
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            return mixBuilderClass.getConstructor(ruleClass).newInstance(rule).let { builder ->
                mixBuilderClass.getMethod("setFormat", AudioFormat::class.java).invoke(builder, format)
                mixBuilderClass.getMethod("setRouteFlags", Int::class.java)
                    .invoke(builder, mixClass.getField("ROUTE_FLAG_LOOP_BACK").getInt(null))
                mixBuilderClass.getMethod("build").invoke(builder)
            }
        }
    }

    /** Lines to Lumbina, from several threads. */
    private class Lumbina(private val out: OutputStream) {
        @Synchronized
        fun say(line: String) {
            runCatching {
                out.write("$line\n".toByteArray())
                out.flush()
            }
        }
    }

    /** A context for a bare process: an activity thread that was never attached to an app. */
    private fun systemContext(): Context {
        val threadClass = Class.forName("android.app.ActivityThread")
        val thread = threadClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        threadClass.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, thread)
        return threadClass.getDeclaredMethod("getSystemContext").invoke(thread) as Context
    }
}
