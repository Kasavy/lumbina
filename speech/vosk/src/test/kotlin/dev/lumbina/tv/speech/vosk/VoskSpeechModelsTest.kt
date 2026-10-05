package dev.lumbina.tv.speech.vosk

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.speech.ModelState
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VoskSpeechModelsTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `download right after delete starts once the old files are gone`() = runBlocking {
        val firstRequest = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val source = ModelSource { _, _ ->
            requests.incrementAndGet()
            firstRequest.countDown()
            release.await() // a download stuck on the network: cancelling doesn't stop it
            throw IOException("offline")
        }
        val store = VoskModelStore(temp.newFolder("models"))
        val models =
            VoskSpeechModels(
                RuntimeEnvironment.getApplication(),
                store,
                ModelInstaller(store, source),
                LumbinaLog {
                        _,
                        _,
                    ->
                },
            )

        models.install("ru")
        firstRequest.await()
        models.delete("ru")
        models.install("ru")
        delay(300)
        // While the first download hangs, the deletion waits for it, and the new one waits for the deletion.
        assertEquals(1, requests.get())
        release.countDown()

        // Each download tries both mirrors: two requests for the first, two for the new one.
        val last = withTimeout(10_000) { models.states.first { it["ru"] is ModelState.Failed && requests.get() == 4 } }
        assertEquals(ModelState.Failed(ModelState.Reason.Network), last["ru"])
    }
}
