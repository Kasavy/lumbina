package dev.lumbina.tv.core.domain.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdatesTest {

    @Test
    fun `a higher number in any part is newer`() {
        assertTrue(isNewer("v0.0.4", "0.0.3"))
        assertTrue(isNewer("0.1.0", "0.0.9"))
        assertTrue(isNewer("1.0", "0.9.9"))
        assertTrue(isNewer("0.0.10", "0.0.9"))
    }

    @Test
    fun `the same or an older version is not newer`() {
        assertFalse(isNewer("v0.0.3", "0.0.3"))
        assertFalse(isNewer("0.0.3", "0.0.3-debug"))
        assertFalse(isNewer("v0.0.2", "0.0.3"))
        assertFalse(isNewer("0.1", "0.1.0"))
    }

    @Test
    fun `what is no version is not newer`() {
        assertFalse(isNewer("nightly", "0.0.3"))
        assertFalse(isNewer("v0.0.4", ""))
    }

    @Test
    fun `the sum is read by the file's name`() {
        val sums = "ABCDEF01  lumbina-v0.0.4.apk\n1234  other.apk\n"
        assertEquals("abcdef01", publishedSum(sums, "lumbina-v0.0.4.apk"))
        assertEquals("1234", publishedSum("1234 *other.apk", "other.apk"))
        assertNull(publishedSum(sums, "missing.apk"))
        assertNull(publishedSum("", "lumbina-v0.0.4.apk"))
    }

    @Test
    fun `the install command keeps its files in the shell's folder, named by its process`() {
        val command = shellInstallCommand(46_312_442, "dev.lumbina.tv/.MainActivity")
        assertTrue(command.startsWith("f=/data/local/tmp/lumbina-update-$$.apk; "))
        // Stopped with the app it replaces, this shell must not be the one to open it again.
        val detached = command.substringAfter("setsid sh -c \"").substringBefore("\"")
        assertTrue("cmd package install -r -S 46312442" in detached)
        assertTrue("am start -n dev.lumbina.tv/.MainActivity" in detached)
    }

    @Test
    fun `the package manager's answer is told apart`() {
        assertEquals(ShellInstall.Done, shellInstallResult("Success\nStarting: Intent { cmp=dev.lumbina.tv }"))
        assertEquals(
            ShellInstall.Refused,
            shellInstallResult("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]"),
        )
        assertEquals(ShellInstall.Refused, shellInstallResult("Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"))
        assertEquals(ShellInstall.Failed, shellInstallResult("Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]"))
        assertEquals(ShellInstall.Failed, shellInstallResult(null))
        assertEquals(ShellInstall.Failed, shellInstallResult(""))
        assertEquals(ShellInstall.Pending, shellInstallResult(".\n.\nPending\n"))
    }

    @Test
    fun `the waiting dots are left out of the answer`() {
        assertEquals("Success", shellInstallAnswer(".\n.\n.\nSuccess\n"))
    }
}
