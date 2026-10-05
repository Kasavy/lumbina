package dev.lumbina.tv.platform.system

import dev.lumbina.tv.core.common.LumbinaComponents
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationListenerNameTest {

    @Test
    fun `the name the access code allows is this listener's`() {
        assertEquals(LumbinaNotificationListener::class.java.name, LumbinaComponents.NOTIFICATION_LISTENER)
    }
}
