package dev.lumbina.tv.core.domain.voice

import dev.lumbina.tv.core.domain.app.App

sealed interface AppMatch {
    data class Found(val app: App) : AppMatch

    data class Ambiguous(val apps: List<App>) : AppMatch

    data object None : AppMatch
}

/**
 * Finds the app a spoken name means. Recognizers write foreign names by ear ("ютуб", "смарт
 * тьюб", "вк видео"), so names and queries are compared as rough Latin sound skeletons, against
 * the label, its first words and the package name, with a little tolerance for mishearing.
 * [aliases] maps a spoken name to names of apps it may mean instead, tried in order when the
 * name matches no label exactly. An app may come more than once, under each of its names.
 */
class AppMatcher(aliases: Map<String, List<String>> = emptyMap()) {

    private val aliases: Map<String, List<String>> = aliases.entries
        .groupBy({ skeleton(it.key) }, { it.value })
        .mapValues { (_, names) -> names.flatten().distinct() }

    fun match(query: String, apps: List<App>): AppMatch {
        val key = skeleton(query)
        val (direct, score) = best(key, apps)
        if (score == 1.0) return direct
        // An alias ("ютуб" → an alternative client) beats a merely similar label ("рутуб").
        val aliased = aliases[key].orEmpty().asSequence().map { best(skeleton(it), apps).first }
            .firstOrNull { it !is AppMatch.None }
        return aliased ?: direct
    }

    private fun best(query: String, apps: List<App>): Pair<AppMatch, Double> {
        if (query.isEmpty()) return AppMatch.None to 0.0
        val scored = apps.map { it to score(query, it) }.filter { it.second.value > 0.0 }
            .sortedByDescending { it.second.value }
            .distinctBy { it.first.id }
        val top = scored.firstOrNull() ?: return AppMatch.None to 0.0
        val tied = scored.filter { top.second.value - it.second.value <= TIE_MARGIN }
        // A name on screen beats a word of a package name that matches as well.
        val close = (tied.filter { it.second.byLabel }.ifEmpty { tied }).map { it.first }
        val match = when {
            close.size == 1 -> AppMatch.Found(close.single())
            close.count { it.isTvApp } == 1 -> AppMatch.Found(close.first { it.isTvApp })
            else -> AppMatch.Ambiguous(close.take(MAX_CHOICES))
        }
        return match to top.second.value
    }

    /** How well a query matches an app, and whether by its label rather than its package name. */
    private data class Score(val value: Double, val byLabel: Boolean)

    /** The best similarity to the app's names, or 0 below the bar; the package name counts for less. */
    private fun score(query: String, app: App): Score {
        val words = app.label.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        val labels = (1..words.size).map { n -> skeleton(words.take(n).joinToString("")) } + skeleton(app.label)
        val packageWords = app.id.packageName.split('.').filter { it !in PACKAGE_NOISE }.map(::skeleton)
        val byLabel = labels.filter { it.isNotEmpty() }.maxOfOrNull { similarity(query, it) } ?: 0.0
        val byPackage = (packageWords.filter { it.isNotEmpty() }.maxOfOrNull { similarity(query, it) } ?: 0.0) *
            PACKAGE_WEIGHT
        val value = maxOf(byLabel, byPackage).takeIf { it >= thresholdFor(query) } ?: 0.0
        return Score(value, byLabel >= byPackage)
    }

    private companion object {
        const val THRESHOLD = 0.75
        const val PACKAGE_WEIGHT = 0.9

        /** Short names: one wrong letter is already a different name ("ютуб" is not "рутуб"). */
        const val SHORT_THRESHOLD = 0.85
        const val SHORT_LENGTH = 5
        const val TIE_MARGIN = 0.04
        const val MAX_CHOICES = 3
        val PACKAGE_NOISE = setOf("com", "ru", "org", "net", "tv", "app", "android", "stable", "dev", "atv", "mobile")

        fun thresholdFor(query: String) = if (query.length <= SHORT_LENGTH) SHORT_THRESHOLD else THRESHOLD
    }
}

/**
 * 1 for equal skeletons, down to 0; very short names must match exactly (too easy to hit by
 * chance). One being the start of the other ("lampa" in "lampas") counts as a close match.
 */
internal fun similarity(a: String, b: String): Double {
    if (a == b) return 1.0
    if (minOf(a.length, b.length) < MIN_FUZZY_LENGTH) return 0.0
    val edits = 1.0 - levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
    return if (a.startsWith(b) || b.startsWith(a)) maxOf(edits, PREFIX_SIMILARITY) else edits
}

private const val PREFIX_SIMILARITY = 0.9

private const val MIN_FUZZY_LENGTH = 4

/**
 * A rough sound skeleton in Latin letters: Cyrillic transliterated, spellings that sound alike
 * merged ("youtube" and "ютуб" both become "iutub"), doubled letters and spaces dropped.
 */
internal fun skeleton(text: String): String {
    val latin = text.lowercase().replace('ё', 'е').map { CYRILLIC[it] ?: it.toString() }.joinToString("")
    var s = latin.filter { it.isLetterOrDigit() }
    SOUNDS.forEach { (from, to) -> s = s.replace(from, to) }
    s = s.replace('y', 'i').replace('w', 'v').replace('q', 'k')
    if (s.length > MIN_FUZZY_LENGTH && s.endsWith('e')) s = s.dropLast(1)
    return s.fold(StringBuilder()) { out, c -> if (out.lastOrNull() != c) out.append(c) else out }.toString()
}

private val SOUNDS = listOf(
    "sch" to "sh", "tch" to "ch", "ph" to "f", "th" to "t", "ck" to "k", "ou" to "u", "oo" to "u",
    "ay" to "e", "ee" to "i", "ea" to "i", "ts" to "z", "tz" to "z", "x" to "ks", "c" to "k", "kh" to "h", "ie" to "i",
)

private val CYRILLIC: Map<Char, String> = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z",
    'и' to "i", 'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
    'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
    'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "iu", 'я' to "ia",
)

private fun levenshtein(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val current = IntArray(b.length + 1)
        current[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
        }
        previous = current
    }
    return previous[b.length]
}
