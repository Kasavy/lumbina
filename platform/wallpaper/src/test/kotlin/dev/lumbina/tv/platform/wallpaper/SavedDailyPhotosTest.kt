package dev.lumbina.tv.platform.wallpaper

import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.DailyPhoto
import dev.lumbina.tv.core.network.HttpGet
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SavedDailyPhotosTest {

    private val context = RuntimeEnvironment.getApplication()
    private val dir = File(context.filesDir, "daily")
    private val log = LumbinaLog { _, _ -> }
    private var online = true
    private val asked = mutableListOf<String>()
    private val http = HttpGet { url ->
        asked += url
        when {
            !online || "broken" in url -> throw IOException("offline")
            "HPImageArchive" in url -> ARCHIVE
            else -> url.toByteArray()
        }
    }
    private val saved = SavedDailyPhotos(context, http, log)

    private fun bing() = BingPhotosOfTheDay(http, saved, log, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `photos are kept newest first with their credits, only the newest ten`() = runBlocking {
        saved.keep(List(6) { DailyPhoto("https://www.bing.com/old$it.jpg", "Old $it") })
        saved.keep(List(6) { DailyPhoto("https://www.bing.com/new$it.jpg", if (it == 0) null else "New $it") })
        val all = saved.all()
        assertEquals(10, all.size)
        assertEquals(listOf(null, "New 1"), all.take(2).map { it.credit })
        assertEquals("Old 3", all.last().credit)
        assertTrue(all.all { it.url.startsWith("file:") })
    }

    @Test
    fun `a photo that fails to download is not listed, and leaves no part behind`() = runBlocking {
        saved.keep(
            listOf(
                DailyPhoto("https://www.bing.com/broken.jpg", "Broken"),
                DailyPhoto("https://www.bing.com/a.jpg", "A"),
            ),
        )
        assertEquals(listOf("A"), saved.all().map { it.credit })
        assertEquals(listOf("index.txt"), dir.list()!!.filter { !it.endsWith(".jpg") })
    }

    @Test
    fun `a file not saved here is left alone, even with the index lost`() = runBlocking {
        dir.mkdirs()
        val stranger = File(dir, "mine.jpg").apply { writeText("x") }
        saved.keep(listOf(DailyPhoto("https://www.bing.com/a.jpg", "A")))
        File(dir, "index.txt").delete()
        saved.keep(listOf(DailyPhoto("https://www.bing.com/b.jpg", "B")))
        assertTrue(stranger.isFile)
        assertEquals(listOf("B"), saved.all().map { it.credit })
    }

    @Test
    fun `offline, Bing's list gives way to the saved photos, and Bing is not asked again at once`() = runBlocking {
        saved.keep(listOf(DailyPhoto("https://www.bing.com/a.jpg", "A lake")))
        online = false
        val bing = bing()
        val photos = bing.photos()
        assertEquals(listOf("A lake"), photos.map { it.credit })
        assertTrue(photos.single().url.startsWith("file:"))
        asked.clear()
        bing.photos()
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun `online, Bing's photos keep their web addresses, saved or not, so the wallpaper finds its place`() =
        runBlocking {
            saved.keep(listOf(DailyPhoto("https://www.bing.com/th?id=OHR.Lake_1920x1080.jpg", "A lake")))
            val photos = bing().photos()
            assertEquals(listOf("https://www.bing.com/th?id=OHR.Lake_1920x1080.jpg"), photos.map { it.url })
        }

    private companion object {
        val ARCHIVE = """{"images":[{"urlbase":"/th?id=OHR.Lake","copyright":"A lake"}]}""".toByteArray()
    }
}
