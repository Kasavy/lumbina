package dev.lumbina.tv.tvprovider

/**
 * The part of SQL `WHERE` and `ORDER BY` that apps pass to the TV Provider, applied to stored
 * rows: `column = ?`, `column = 'text'`, `column = 42` and `column IS NULL` joined by AND, and
 * `column [ASC|DESC], …` orders. `value` reads a row's column.
 */
internal object RowSelection {

    /** The rows matching [selection] (all for a blank one); null when it uses more than the above. */
    fun <T> select(rows: List<T>, selection: String?, args: Array<out String>?, value: (T, String) -> Any?): List<T>? {
        val conditions = parse(selection, args.orEmpty().toList()) ?: return null
        return rows.filter { row -> conditions.all { (column, expected) -> text(value(row, column)) == expected } }
    }

    /** [rows] in [sortOrder]; unchanged for a blank or unsupported one. */
    fun <T> sort(rows: List<T>, sortOrder: String?, value: (T, String) -> Any?): List<T> {
        if (sortOrder.isNullOrBlank()) return rows
        val terms = sortOrder.split(',').map { term ->
            val match = ORDER.matchEntire(term.trim()) ?: return rows
            match.groupValues[1] to match.groupValues[2].equals("DESC", ignoreCase = true)
        }
        return rows.sortedWith { a, b ->
            terms.firstNotNullOfOrNull { (column, descending) ->
                compare(value(a, column), value(b, column)).takeIf { it != 0 }?.let { if (descending) -it else it }
            } ?: 0
        }
    }

    /** Column → expected text (null for IS NULL); null when [selection] can't be read or [args] don't fit. */
    private fun parse(selection: String?, args: List<String>): List<Pair<String, String?>>? {
        val terms = if (selection.isNullOrBlank()) emptyList() else selection.trim().split(AND)
        val remaining = args.iterator()
        val conditions = terms.map { condition(it, remaining) }
        return conditions.takeIf { null !in it && !remaining.hasNext() }?.filterNotNull()
    }

    private fun condition(term: String, args: Iterator<String>): Pair<String, String?>? {
        val (column, operand) = CONDITION.matchEntire(term.trim())?.destructured ?: return null
        val expected = when {
            operand.isEmpty() -> null
            operand == "?" -> if (args.hasNext()) args.next() else return null
            operand.startsWith('\'') -> operand.substring(1, operand.length - 1).replace("''", "'")
            else -> operand
        }
        return column to expected
    }

    /** A stored value as SQLite would compare it with text: whole numbers without a fraction. */
    private fun text(value: Any?): String? = when (value) {
        null -> null
        is Boolean -> if (value) "1" else "0"
        is Double if value % 1.0 == 0.0 -> value.toLong().toString()
        else -> value.toString()
    }

    /** SQLite's order: NULL, then numbers, then text (and anything else). */
    private fun compare(a: Any?, b: Any?): Int {
        val rank = rank(a).compareTo(rank(b))
        return when {
            rank != 0 -> rank
            a is Number && b is Number -> a.toDouble().compareTo(b.toDouble())
            a is String && b is String -> a.compareTo(b)
            else -> 0
        }
    }

    private fun rank(value: Any?): Int = when (value) {
        null -> 0
        is Number -> 1
        else -> 2
    }

    private val AND = Regex("\\s+AND\\s+", RegexOption.IGNORE_CASE)
    private val CONDITION = Regex(
        "([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=\\s*(\\?|'(?:[^']|'')*'|-?\\d+)|\\s+IS\\s+NULL)",
        RegexOption.IGNORE_CASE,
    )
    private val ORDER = Regex("([A-Za-z_][A-Za-z0-9_]*)(?:\\s+(ASC|DESC))?", RegexOption.IGNORE_CASE)
}
