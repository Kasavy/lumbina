package dev.lumbina.tv.speech.vosk

import android.content.ComponentName
import android.os.Bundle
import android.os.Looper
import dev.lumbina.tv.core.common.LumbinaLog
import java.io.File
import java.io.IOException
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RemoteSpeechEngineTest {

    @get:Rule val temp = TemporaryFolder()

    private val app get() = RuntimeEnvironment.getApplication()
    private val shadowApp get() = shadowOf(app)
    private val component get() = ComponentName(app, VoiceService::class.java)
    private val store by lazy { VoskModelStore(temp.newFolder("models")) }
    private val engine by lazy {
        RemoteSpeechEngine(app, store, LumbinaLog { _, _ -> }, IDLE_MILLIS, CONNECT_TIMEOUT_MILLIS)
    }

    private val session = object : IVoiceSession.Stub() {
        override fun accept(pcm: ByteArray, length: Int) = Unit

        override fun partial(): String = ""

        override fun finish(): Bundle = Bundle()

        override fun close() = Unit
    }

    private fun voiceProcess(newSession: () -> IVoiceSession = { session }) = object : IVoiceEngine.Stub() {
        override fun newSession(load: List<String>, languages: List<String>): IVoiceSession = newSession()
    }

    @Before
    fun installRussian() {
        File(store.directory("ru"), "conf").mkdirs()
        File(store.directory("ru"), "conf/model.conf").writeText("")
        File(store.directory("ru"), "am").mkdirs()
        assertTrue(engine.prepare(listOf("ru")))
    }

    /** Runs [block] off the main thread, as voice input does, while the main looper delivers the binding. */
    private fun <T> offMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        val worker = thread { result.set(runCatching(block)) }
        while (worker.isAlive) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return result.get().getOrThrow()
    }

    private fun idleFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    @Test
    fun `a session binds the voice process, which is let go once idle`() {
        shadowApp.setComponentNameAndServiceForBindService(component, voiceProcess())

        offMain { engine.newSession(listOf("ru")) }.close()

        assertEquals(1, shadowApp.boundServiceConnections.size)
        idleFor(IDLE_MILLIS + 1)
        assertEquals(1, shadowApp.unboundServiceConnections.size)
    }

    @Test
    fun `an open session keeps the voice process`() {
        shadowApp.setComponentNameAndServiceForBindService(component, voiceProcess())

        offMain { engine.newSession(listOf("ru")) }
        idleFor(2 * IDLE_MILLIS)

        assertEquals(0, shadowApp.unboundServiceConnections.size)
    }

    @Test
    fun `any failure opening a session still lets the voice process go`() {
        shadowApp.setComponentNameAndServiceForBindService(
            component,
            voiceProcess { throw IllegalArgumentException("broken") },
        )

        val failure = runCatching { offMain { engine.newSession(listOf("ru")) } }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        idleFor(IDLE_MILLIS + 1)
        assertEquals(1, shadowApp.unboundServiceConnections.size)
    }

    @Test
    fun `a voice process that doesn't come up is let go, and the next session binds afresh`() {
        shadowApp.setComponentNameAndServiceForBindService(component, voiceProcess())

        // The main looper stands still, so the connection doesn't arrive in time.
        var failure: Throwable? = null
        thread { failure = runCatching { engine.newSession(listOf("ru")) }.exceptionOrNull() }.join()

        assertTrue("$failure", failure is IOException)
        assertEquals(1, shadowApp.unboundServiceConnections.size)
        idleFor(0)
        offMain { engine.newSession(listOf("ru")) }.close()
        assertEquals(1, shadowApp.boundServiceConnections.size)
    }

    private companion object {
        const val IDLE_MILLIS = 1_000L
        const val CONNECT_TIMEOUT_MILLIS = 300L
    }
}
