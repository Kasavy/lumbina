package dev.lumbina.tv.vendor.hisense

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class HisenseVolumeTest {

    private val context = RuntimeEnvironment.getApplication()
    private val receiver = ComponentName("com.hisense.tv.baseservice", "com.hisense.tv.baseservice.MainReceiver")

    @Test
    fun `without TvBaseService's receiver the volume says so and sends nothing`() {
        assertFalse(HisenseVolume(context).change(4))
        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
    }

    @Test
    fun `with the receiver the change goes to it`() {
        shadowOf(context.packageManager).apply {
            addReceiverIfNotPresent(receiver)
            addIntentFilterForReceiver(receiver, IntentFilter("com.hisense.tv.aiot.VOLUME"))
        }
        assertTrue(HisenseVolume(context).change(-4))
        val sent: Intent = shadowOf(context).broadcastIntents.single()
        assertEquals(receiver, sent.component)
        assertEquals(5013, sent.getIntExtra("keyCode", 0))
    }
}
