package dev.lumbina.tv.platform.accessibility

import android.text.InputType.TYPE_CLASS_NUMBER
import android.text.InputType.TYPE_CLASS_TEXT
import android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
import android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
import android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT
import android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
import android.text.InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendsOnSubmitTest {

    @Test
    fun `messages, multi-line text and passwords, a numeric PIN too, are not submitted`() {
        assertTrue(sendsOnSubmit(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_SHORT_MESSAGE))
        assertTrue(sendsOnSubmit(TYPE_CLASS_TEXT or TYPE_TEXT_FLAG_MULTI_LINE))
        assertTrue(sendsOnSubmit(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(sendsOnSubmit(TYPE_CLASS_NUMBER or TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test
    fun `a search box, a plain number or an email subject are`() {
        assertFalse(sendsOnSubmit(TYPE_CLASS_TEXT))
        assertFalse(sendsOnSubmit(TYPE_CLASS_NUMBER))
        assertFalse(sendsOnSubmit(TYPE_CLASS_TEXT or TYPE_TEXT_VARIATION_EMAIL_SUBJECT))
        // The number class's password variation has the same value as a text variation it isn't.
        assertFalse(sendsOnSubmit(TYPE_CLASS_TEXT or TYPE_NUMBER_VARIATION_PASSWORD))
    }
}
