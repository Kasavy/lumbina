package dev.lumbina.tv.core.domain.home

/** A row of the home screen below Favorites (which always comes first). */
sealed interface HomeRow {
    val key: String

    /** False for rows the user has to switch on. */
    val defaultVisible: Boolean get() = true

    data object WatchNext : HomeRow {
        override val key = "watch_next"
    }

    /** The apps opened last, as in the app switcher (Favorites too); off until the user wants it. */
    data object Opened : HomeRow {
        override val key = "opened"
        override val defaultVisible = false
    }

    /** The apps opened most often lately that are not in Favorites; off until the user wants it. */
    data object Frequent : HomeRow {
        override val key = "frequent"
        override val defaultVisible = false
    }

    data object Recent : HomeRow {
        override val key = "recent"
    }

    /** The channels apps publish, one tab each. */
    data object Recommendations : HomeRow {
        override val key = "recommendations"
    }

    data class Category(val id: String) : HomeRow {
        override val key = "category:$id"
    }
}

data class HomeRowEntry(val row: HomeRow, val visible: Boolean)

/**
 * The rows in the user's [order]; rows it does not name yet (a new category, a row added in an
 * update) follow in the default order. A row is visible unless [hidden], or for rows that start
 * off ([HomeRow.defaultVisible]), only when [shown].
 */
fun arrangeRows(
    order: List<String>,
    hidden: Set<String>,
    categoryIds: List<String>,
    shown: Set<String> = emptySet(),
): List<HomeRowEntry> {
    val all = listOf(HomeRow.WatchNext, HomeRow.Opened, HomeRow.Frequent, HomeRow.Recent) +
        categoryIds.map(HomeRow::Category) + HomeRow.Recommendations
    val byKey = all.associateBy { it.key }
    val known = order.mapNotNull(byKey::get).distinct()
    return (known + all.filter { it !in known }).map { row ->
        HomeRowEntry(row, if (row.defaultVisible) row.key !in hidden else row.key in shown)
    }
}

/** The order after moving [key] [by] places; the result names every current row. */
fun List<HomeRowEntry>.movedRow(key: String, by: Int): List<String> {
    val keys = map { it.row.key }
    return keys.moved(keys.indexOf(key), by) ?: keys
}
