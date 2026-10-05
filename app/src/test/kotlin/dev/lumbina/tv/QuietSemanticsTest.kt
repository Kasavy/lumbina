package dev.lumbina.tv

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * QuietSemantics reaches into Compose by name; a Compose update that renames these turns it off
 * quietly (one log line). This fails first.
 */
class QuietSemanticsTest {

    @Test
    fun `Compose still has the fields QuietSemantics sets`() {
        val view = Class.forName("androidx.compose.ui.platform.AndroidComposeView")
        val delegate = view.getDeclaredField(QuietSemantics.DELEGATE).type
        val services = delegate.getDeclaredField(QuietSemantics.SERVICES)
        assertEquals(List::class.java, services.type)
    }
}
