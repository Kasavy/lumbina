package dev.lumbina.tv.platform.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechAppPartialsTest {

    @Test
    fun `a settled part and a changing part are one text`() {
        assertEquals("home", soFar(listOf("", "home")))
        assertEquals("Открой Smart T", soFar(listOf("Открой", " Smart T")))
        assertEquals("Home Alone", soFar(listOf("Home Alone", "")))
    }

    @Test
    fun `an app known to answer in parts has them joined though no space tells`() {
        assertEquals("Opening", soFar(listOf("Open", "ing"), parts = true))
        assertEquals("Open", soFar(listOf("Open", "ing")))
    }

    @Test
    fun `alternatives give the likeliest`() {
        assertEquals("home alone", soFar(listOf("home alone", "home a loan")))
        assertEquals("one", soFar(listOf("one")))
        assertEquals("", soFar(emptyList()))
    }
}
