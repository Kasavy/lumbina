package dev.lumbina.tv.core.domain.voice

/**
 * Durations as speech recognizers write them: "тридцать минут", "полтора часа", "через час",
 * "два с половиной часа", "два часа тридцать минут", "twenty five minutes", "a hundred minutes",
 * "an hour and a half", or digits ("45 минут").
 */
@Suppress("MagicNumber") // the numbers are the vocabulary
internal object NumberWords {

    /** Minutes in [phrase], or null when it names no duration. */
    fun minutes(phrase: String): Int? {
        val words = phrase.split(' ').filter { it.isNotEmpty() }
        return FIXED_PHRASES[words.joinToString(" ")] ?: counted(words)
    }

    /**
     * One or more "<amount> [and a half] <unit> [and a half]", added up ("два часа тридцать минут");
     * without an amount ("через час") the amount is one. A number left after hours is minutes
     * ("два часа тридцать").
     */
    private fun counted(words: List<String>): Int? {
        var total: Int? = null
        var rest = words
        var lastUnit = 0
        while (true) {
            val (unitIndex, perUnit) = rest.withIndex().firstNotNullOfOrNull { (i, word) ->
                unitOf(word)?.let { i to it }
            } ?: return total?.plus(if (lastUnit == HOUR) minutesAfterHours(rest) else 0)
            lastUnit = perUnit
            val before = rest.take(unitIndex)
            val halfBefore = HALF_SUFFIXES.firstOrNull { before.size > it.size && before.takeLast(it.size) == it }
            val spoken = before.dropLast(halfBefore?.size ?: 0).filter { it !in IGNORED }
            val amount = if (spoken.isEmpty()) 1 else amountOf(spoken) ?: return null
            rest = rest.drop(unitIndex + 1)
            val halfAfter = HALF_SUFFIXES.firstOrNull { rest.take(it.size) == it }
            rest = rest.drop(halfAfter?.size ?: 0)
            val half = halfBefore != null || halfAfter != null
            total = (total ?: 0) + amount * perUnit + if (half) perUnit / 2 else 0
        }
    }

    /** "тридцать" in "два часа тридцать"; 0 when what is left is not a number. */
    private fun minutesAfterHours(rest: List<String>): Int =
        rest.filter { it !in IGNORED }.takeIf { it.isNotEmpty() }?.let(::amountOf) ?: 0

    private fun unitOf(word: String): Int? = when {
        word.startsWith("минут") || word.startsWith("minute") || word == "min" -> 1
        word.startsWith("час") || word.startsWith("hour") -> HOUR
        else -> null
    }

    private const val HOUR = 60

    /**
     * Number words as spoken ("двадцать пять" = 25): added up, except that a hundred multiplies what
     * came before it ("a hundred" = 100, "one hundred twenty" = 120); null if any word is not a number.
     */
    private fun amountOf(words: List<String>): Int? {
        if (words.isEmpty()) return null
        return words.fold(0) { sum, word ->
            when {
                word in HUNDREDS -> maxOf(sum, 1) * 100
                else -> sum + (word.toIntOrNull() ?: VALUES[word] ?: return null)
            }
        }
    }

    private val IGNORED = setOf("and", "и")
    private val HUNDREDS = setOf("hundred", "hundreds")
    private val HALF_SUFFIXES = listOf(listOf("and", "a", "half"), listOf("с", "половиной"))

    private val FIXED_PHRASES = mapOf(
        "полчаса" to 30,
        "пол часа" to 30,
        "полтора часа" to 90,
        "half an hour" to 30,
        "half hour" to 30,
        "an hour and a half" to 90,
        "hour and a half" to 90,
    )

    private val VALUES = mapOf(
        "один" to 1, "одну" to 1, "одна" to 1, "a" to 1, "an" to 1, "one" to 1,
        "два" to 2, "две" to 2, "two" to 2, "три" to 3, "three" to 3, "четыре" to 4, "four" to 4,
        "пять" to 5, "five" to 5, "шесть" to 6, "six" to 6, "семь" to 7, "seven" to 7,
        "восемь" to 8, "eight" to 8, "девять" to 9, "nine" to 9, "десять" to 10, "ten" to 10,
        "одиннадцать" to 11, "eleven" to 11, "двенадцать" to 12, "twelve" to 12,
        "тринадцать" to 13, "thirteen" to 13, "четырнадцать" to 14, "fourteen" to 14,
        "пятнадцать" to 15, "fifteen" to 15, "шестнадцать" to 16, "sixteen" to 16,
        "семнадцать" to 17, "seventeen" to 17, "восемнадцать" to 18, "eighteen" to 18,
        "девятнадцать" to 19, "nineteen" to 19, "двадцать" to 20, "twenty" to 20,
        "тридцать" to 30, "thirty" to 30, "сорок" to 40, "сорока" to 40, "forty" to 40,
        "пятьдесят" to 50, "fifty" to 50, "шестьдесят" to 60, "sixty" to 60,
        "семьдесят" to 70, "seventy" to 70, "восемьдесят" to 80, "eighty" to 80,
        "девяносто" to 90, "ninety" to 90, "сто" to 100,
    )
}
