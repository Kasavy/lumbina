package dev.lumbina.tv.platform.system

import android.content.Context
import android.content.Intent
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.OwnLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AndroidAppLanguageTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val language = AndroidAppLanguage(context)

    private fun told(): List<Intent> = shadowOf(RuntimeEnvironment.getApplication()).broadcastIntents.filter {
        it.action ==
            LumbinaActions.LANGUAGE_CHANGED
    }

    @Test
    fun `before Android 13 the choice is kept in Lumbina's file and the processes are told`() {
        assertNull(language.current())
        language.set("ru")
        assertEquals("ru", language.current())
        assertEquals("ru", OwnLanguage.read(context.filesDir))
        assertEquals(context.packageName, told().single().`package`)
        language.set(null)
        assertNull(language.current())
        assertEquals(2, told().size)
    }
}
