package dev.lumbina.tv.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditLinesTest {

    @Test
    fun `whose the photo is goes on a line of its own, without the brackets`() {
        assertEquals(
            "Château de Castelnaud, France\n© someone/Getty Images",
            creditLines("Château de Castelnaud, France (© someone/Getty Images)"),
        )
    }

    @Test
    fun `brackets in what the photo shows stay where they are`() {
        assertEquals("A lake (at dawn), Norway\n© A. Author", creditLines("A lake (at dawn), Norway (© A. Author)"))
    }

    @Test
    fun `a credit of another shape is written as it is`() {
        assertEquals("Photo by A. Author", creditLines("Photo by A. Author"))
        assertEquals("© A. Author", creditLines("© A. Author"))
    }
}
