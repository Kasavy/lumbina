package dev.lumbina.tv.core.domain.backdrop

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NightHoursTest {

    @Test
    fun `night runs from ten in the evening until seven in the morning`() {
        val night = (0..23).filter(::isNightHour)
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 22, 23), night)
    }
}
