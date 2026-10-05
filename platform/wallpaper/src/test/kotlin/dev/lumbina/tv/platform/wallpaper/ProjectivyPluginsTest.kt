package dev.lumbina.tv.platform.wallpaper

import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import dev.lumbina.tv.core.common.LumbinaLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProjectivyPluginsTest {

    private val guarded = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean =
            throw SecurityException("Not allowed to bind to service Intent")
    }

    @Test
    fun `a plugin whose service needs a permission gives no wallpapers instead of throwing`() = runTest {
        val logged = mutableListOf<String>()
        val plugins = ProjectivyPlugins(guarded, LumbinaLog { _, message -> logged += message })
        assertEquals(emptyList<Any>(), plugins.wallpapers("com.example.plugin/.Wallpapers"))
        assertTrue(logged.any { "SecurityException" in it })
    }
}
