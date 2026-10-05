package dev.lumbina.tv.speech.vosk

import dev.lumbina.tv.core.domain.speech.ModelState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelInstallerTest {

    @get:Rule val temp = TemporaryFolder()

    private val store by lazy { VoskModelStore(temp.newFolder("models")) }
    private val archive = zip(
        "vosk-model-test/" to null,
        "vosk-model-test/conf/model.conf" to "--min-active=200",
        "vosk-model-test/am/final.mdl" to "acoustic model",
    )
    private val model = VoskModel("ru", "vosk-model-test", archive.size.toLong(), sha256(archive))
    private val requestedOffsets = mutableListOf<Long>()
    private val states = mutableListOf<ModelState>()

    private fun server(bytes: ByteArray = archive, honorsRange: Boolean = true) = ModelSource { _, offset ->
        requestedOffsets += offset
        val start = if (honorsRange) offset else 0
        Opened(start, ByteArrayInputStream(bytes, start.toInt(), bytes.size - start.toInt()))
    }

    private suspend fun install(source: ModelSource) = ModelInstaller(store, source).install(model) { states += it }

    @Test
    fun `installs the model without its top-level folder and removes the archive`() = runTest {
        install(server())
        assertEquals("--min-active=200", File(store.directory("ru"), "conf/model.conf").readText())
        assertTrue(store.isInstalled("ru"))
        assertFalse(File(store.downloads, "vosk-model-test.zip").exists())
        assertFalse(store.staging("ru").exists())
        assertEquals(ModelState.Unpacking, states.last())
        assertEquals(ModelState.Downloading(model.sizeBytes, model.sizeBytes), states[states.size - 2])
    }

    @Test
    fun `resumes a partial download`() = runTest {
        store.downloads.mkdirs()
        File(store.downloads, "vosk-model-test.zip").writeBytes(archive.copyOf(archive.size / 2))
        install(server())
        assertEquals(listOf(archive.size / 2L), requestedOffsets)
        assertTrue(store.isInstalled("ru"))
    }

    @Test
    fun `starts over when the server ignores the range`() = runTest {
        store.downloads.mkdirs()
        File(store.downloads, "vosk-model-test.zip").writeBytes(archive.copyOf(archive.size / 2))
        install(server(honorsRange = false))
        assertTrue(store.isInstalled("ru"))
    }

    @Test
    fun `a corrupt download is deleted and nothing is installed`() = runTest {
        val corrupt = archive.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertFailure(ModelState.Reason.Corrupt) { install(server(corrupt)) }
        assertFalse(File(store.downloads, "vosk-model-test.zip").exists())
        assertFalse(store.isInstalled("ru"))
    }

    @Test
    fun `a network error on every mirror keeps what was downloaded`() = runTest {
        val flaky = ModelSource { _, offset ->
            Opened(
                offset,
                FailingAfter(
                    archive.copyOfRange(offset.toInt(), archive.size),
                    archive.size / 2 - offset.toInt(),
                ),
            )
        }
        assertFailure(ModelState.Reason.Network) { install(flaky) }
        assertEquals(archive.size / 2L, File(store.downloads, "vosk-model-test.zip").length())
    }

    @Test
    fun `a stalled mirror hands over to the next one at the same byte`() = runTest {
        val stallsHalfway = ModelSource { url, offset ->
            requestedOffsets += offset
            if (url.contains("alphacephei")) {
                Opened(offset, FailingAfter(archive, archive.size / 2))
            } else {
                Opened(offset, ByteArrayInputStream(archive, offset.toInt(), archive.size - offset.toInt()))
            }
        }
        install(stallsHalfway)
        assertEquals(listOf(0L, archive.size / 2L), requestedOffsets)
        assertTrue(store.isInstalled("ru"))
    }

    @Test
    fun `entries escaping the model folder are rejected`() = runTest {
        val evil = zip("m/conf/model.conf" to "x", "m/am/a" to "x", "m/../../evil" to "x")
        val evilModel = VoskModel("ru", "evil", evil.size.toLong(), sha256(evil))
        assertFailure(ModelState.Reason.Corrupt) {
            ModelInstaller(store, server(evil)).install(evilModel) {}
        }
        assertFalse(File(store.root.parentFile, "evil").exists())
        assertFalse(store.isInstalled("ru"))
    }

    @Test
    fun `an archive that is not a model is rejected and the old model kept`() = runTest {
        install(server())
        val notModel = zip("m/readme.txt" to "hello")
        val other = VoskModel("ru", "other", notModel.size.toLong(), sha256(notModel))
        assertFailure(ModelState.Reason.Corrupt) { ModelInstaller(store, server(notModel)).install(other) {} }
        assertTrue(store.isInstalled("ru"))
    }

    private suspend fun assertFailure(reason: ModelState.Reason, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: InstallFailure) {
            assertEquals(reason, e.reason)
        }
    }

    private class FailingAfter(bytes: ByteArray, private val limit: Int) : InputStream() {
        private val input = ByteArrayInputStream(bytes)
        private var position = 0

        override fun read(): Int = if (position++ >= limit) throw IOException("connection reset") else input.read()

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position >= limit) throw IOException("connection reset")
            val read = input.read(b, off, minOf(len, limit - position))
            position += read
            return read
        }
    }

    private companion object {
        fun zip(vararg entries: Pair<String, String?>): ByteArray = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (name, text) ->
                    zip.putNextEntry(ZipEntry(name))
                    text?.let { zip.write(it.toByteArray()) }
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
