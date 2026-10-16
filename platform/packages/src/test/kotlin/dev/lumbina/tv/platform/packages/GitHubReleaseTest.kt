package dev.lumbina.tv.platform.packages

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseTest {

    private val apk = GitHubRelease.Asset("lumbina-v0.0.4.apk", 46, "https://example.org/lumbina.apk")
    private val sums = GitHubRelease.Asset("SHA256SUMS.txt", 1, "https://example.org/sums")

    @Test
    fun `a release names its package and its checksums, the version without the v`() {
        val release = GitHubRelease("v0.0.4", listOf(sums, apk)).toRelease()!!
        assertEquals("0.0.4", release.version)
        assertEquals("lumbina-v0.0.4.apk", release.apkName)
        assertEquals("https://example.org/lumbina.apk", release.apkUrl)
        assertEquals(46L, release.bytes)
        assertEquals("https://example.org/sums", release.sumsUrl)
    }

    @Test
    fun `a release without its package or its checksums is none`() {
        assertNull(GitHubRelease("v0.0.4", listOf(sums)).toRelease())
        assertNull(GitHubRelease("v0.0.4", listOf(apk)).toRelease())
    }

    @Test
    fun `GitHub's answer is read, with what is not needed left out`() {
        val answer = """
            {"tag_name": "v0.0.4", "draft": false, "assets": [
              {"name": "lumbina-v0.0.4.apk", "size": 46, "browser_download_url": "https://example.org/lumbina.apk"},
              {"name": "SHA256SUMS.txt", "size": 1, "browser_download_url": "https://example.org/sums"}
            ]}
        """.trimIndent()
        val release = Json { ignoreUnknownKeys = true }.decodeFromString<GitHubRelease>(answer).toRelease()!!
        assertEquals("https://example.org/sums", release.sumsUrl)
    }

    @Test
    fun `a signature of the checksums is not taken for them`() {
        val signature = GitHubRelease.Asset("SHA256SUMS.asc", 1, "https://example.org/sums.asc")
        assertEquals(
            "https://example.org/sums",
            GitHubRelease("v0.0.4", listOf(signature, apk, sums)).toRelease()!!.sumsUrl,
        )
    }
}
