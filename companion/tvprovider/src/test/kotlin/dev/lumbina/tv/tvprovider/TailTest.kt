package dev.lumbina.tv.tvprovider

import org.junit.Assert.assertEquals
import org.junit.Test

class TailTest {

    private val log = "one\ntwo\nthree\n"

    @Test
    fun `a log that fits is given whole`() {
        assertEquals(log, tailOf(log, log.length))
    }

    @Test
    fun `a cut inside a line starts at the next one`() {
        assertEquals("three\n", tailOf(log, 8))
    }

    @Test
    fun `a cut where a line begins keeps that line`() {
        assertEquals("two\nthree\n", tailOf(log, 10))
    }

    @Test
    fun `past the last line break nothing whole is left`() {
        assertEquals("", tailOf("one\ntwo-without-end", 5))
    }
}
