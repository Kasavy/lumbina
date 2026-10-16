package dev.lumbina.tv.core.domain.update

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * [shellInstallCommand] run by a real `sh`, with the device's `cmd`, `am`, `setsid` and `sleep`
 * played by scripts: where there is no POSIX shell (Windows), it is skipped.
 */
class ShellInstallScriptTest {

    @TempDir
    lateinit var root: File

    private val bin get() = File(root, "bin")
    private val tmp get() = File(root, "tmp")
    private val started get() = File(root, "started")
    private val installed get() = File(root, "installed")

    @BeforeEach
    fun fakes() {
        assumeTrue(File("/bin/sh").canExecute() && !System.getProperty("os.name").startsWith("Windows"))
        bin.mkdirs()
        tmp.mkdirs()
        // The package manager reads the package from its input and says what $ANSWER says.
        script("cmd", "cat > '$installed'; echo \"\$ANSWER\"")
        script("am", "echo \"\$@\" > '$started'")
        script("setsid", "exec \"\$@\"")
        // The waiting loop's second goes by quickly; the answer is still kept a while.
        script("sleep", "if [ \"\$1\" = 1 ]; then exec /bin/sleep 0.05; else exec /bin/sleep 1; fi")
    }

    @Test
    fun `installed, the app is opened and the answer read`() {
        val (code, out) = run(PACKAGE, answer = "Success")
        assertEquals(0, code)
        assertEquals(ShellInstall.Done, shellInstallResult(out))
        assertArrayEquals(PACKAGE, installed.readBytes())
        assertEquals("start -n $ACTIVITY", started.readText().trim())
        assertTrue(waitClean())
    }

    @Test
    fun `refused, the app is not opened and no file is left`() {
        val (_, out) = run(PACKAGE, answer = "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]")
        assertEquals(ShellInstall.Refused, shellInstallResult(out))
        assertFalse(started.exists())
        assertTrue(waitClean())
    }

    @Test
    fun `a package that came short is not installed`() {
        val (code, _) = run(PACKAGE.copyOf(PACKAGE.size / 2), answer = "Success", bytes = PACKAGE.size.toLong())
        assertEquals(1, code)
        assertFalse(installed.exists())
        assertTrue(leftOver().isEmpty())
    }

    private fun run(input: ByteArray, answer: String, bytes: Long = input.size.toLong()): Pair<Int, String> {
        val command = shellInstallCommand(bytes, ACTIVITY, tmp.path)
        val process = ProcessBuilder("/bin/sh", "-c", command).apply {
            environment()["PATH"] = "${bin.path}:/usr/bin:/bin"
            environment()["ANSWER"] = answer
        }.start()
        process.outputStream.use { it.write(input) }
        val out = process.inputStream.bufferedReader().readText()
        assertTrue(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        return process.exitValue() to out
    }

    private fun leftOver(): List<String> = tmp.list().orEmpty().toList()

    /** The answer is kept a second after the install (the fake `sleep`), then removed. */
    private fun waitClean(): Boolean {
        repeat(CLEAN_TRIES) {
            if (leftOver().isEmpty()) return true
            Thread.sleep(CLEAN_STEP_MILLIS)
        }
        return false
    }

    private fun script(name: String, body: String) {
        File(bin, name).apply {
            writeText("#!/bin/sh\n$body\n")
            setExecutable(true)
        }
    }

    private companion object {
        const val ACTIVITY = "dev.lumbina.tv/.MainActivity"
        val PACKAGE = ByteArray(4096) { (it % 251).toByte() }
        const val TIMEOUT_SECONDS = 20L
        const val CLEAN_TRIES = 50
        const val CLEAN_STEP_MILLIS = 100L
    }
}
