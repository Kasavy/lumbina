package dev.lumbina.tv.core.domain.access

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PermissionAnswerTest {

    private fun of(granted: Boolean = false, before: Boolean = false, after: Boolean = false, millis: Long = 3_000) =
        PermissionAnswer.of(granted, before, after, millis)

    @Test
    fun `a grant is a grant`() {
        assertEquals(PermissionAnswer.Granted, of(granted = true))
    }

    @Test
    fun `a first no may be asked again`() {
        assertEquals(PermissionAnswer.Denied, of(before = false, after = true))
    }

    @Test
    fun `a no after an earlier one, with the rationale gone, is for good`() {
        assertEquals(PermissionAnswer.DeniedForGood, of(before = true, after = false))
    }

    @Test
    fun `a dialog left with Back is no answer`() {
        val answer = of(before = false, after = false, millis = 2_500)
        assertEquals(PermissionAnswer.NoAnswer, answer)
        assertFalse(answer.answered)
    }

    @Test
    fun `the system refusing at once means the user chose not to be asked`() {
        assertEquals(PermissionAnswer.DeniedForGood, of(before = false, after = false, millis = 40))
    }
}
