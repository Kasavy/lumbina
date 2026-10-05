package dev.lumbina.tv.core.domain.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LanguagePickerTest {

    @Test
    fun `keeps the primary language unless another is clearly more confident`() {
        val primary = Hypothesis("ru", "кустики", 0.58)
        val filler = Hypothesis("en", "what did you", 0.71)
        assertEquals(primary, LanguagePicker.pick(listOf(primary, filler)))

        val bladeRu = Hypothesis("ru", "клайду рано", 0.51)
        val bladeEn = Hypothesis("en", "blade runner", 1.0)
        assertEquals(bladeEn, LanguagePicker.pick(listOf(bladeRu, bladeEn)))

        val tie = Hypothesis("en", "proper", 1.0)
        val tropa = Hypothesis("ru", "тропа", 1.0)
        assertEquals(tropa, LanguagePicker.pick(listOf(tropa, tie)))
    }

    @Test
    fun `never picks empty text`() {
        val empty = Hypothesis("ru", "", 1.0)
        val en = Hypothesis("en", "park", 0.3)
        assertEquals(en, LanguagePicker.pick(listOf(empty, en)))
        assertNull(LanguagePicker.pick(listOf(empty)))
    }
}
