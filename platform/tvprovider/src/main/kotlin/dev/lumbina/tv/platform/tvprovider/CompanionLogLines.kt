package dev.lumbina.tv.platform.tvprovider

/**
 * The end of Lumbina TV Provider's log ("date time package action …", `CompanionLog` in the
 * companion, which gives its last `LOG_CHARS`) for a report: what each app did there, counted
 * ("12 ru.app insert preview_program"; plain updates are not logged), then the last refusals whole,
 * with the time and the reason.
 */
internal fun companionLogLines(log: String): List<String> {
    val entries = log.lines()
        .filter { it.isNotBlank() }
        .map { line -> line to logAction(line.split(' ', limit = 3).getOrElse(2) { "" }) }
    if (entries.isEmpty()) return listOf("its log: empty")
    val counts = entries
        .groupingBy { it.second }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .map { (action, count) -> "$count $action" }
    val refusals = entries
        .filter { (_, action) -> action.endsWith(" refused") || action.endsWith(" unsupported selection") }
        .map { it.first }
        .takeLast(LAST_REFUSALS)
    val refused = if (refusals.isEmpty()) emptyList() else listOf("last refusals:") + refusals
    return listOf("its log as far as given, app by app:") + counts + refused
}

/**
 * An entry without what differs each time: "ru.app insert channel → 5 Popular" is "ru.app insert
 * channel", "ru.app delete content://…/7 → 1" is "ru.app delete content://…".
 */
private fun logAction(entry: String): String = entry
    .substringBefore(" → ")
    .substringBefore('?')
    .substringBefore(": ")
    .replace(TRAILING_ID, "")

private val TRAILING_ID = Regex("/\\d+$")

private const val LAST_REFUSALS = 30
