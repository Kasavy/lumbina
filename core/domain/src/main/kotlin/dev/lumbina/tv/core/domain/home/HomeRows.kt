package dev.lumbina.tv.core.domain.home

/** A row of the home screen; the user orders them and switches them on and off. */
sealed interface HomeRow {
    val key: String

    /** False for rows the user has to switch on. */
    val defaultVisible: Boolean get() = true

    /** The apps the user put first: a row like the others, first until moved. */
    data object Favorites : HomeRow {
        override val key = HomeSection.FAVORITES
    }

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

    /** The channels apps publish: one block with a tab each, or a row each ([RecommendationsView]). */
    data object Recommendations : HomeRow {
        override val key = "recommendations"
    }

    data class Category(val id: String) : HomeRow {
        override val key = "category:$id"
    }

    /** "Apps": the first apps of the "All apps" screen and a tile that opens it. */
    data object AllApps : HomeRow {
        override val key = "all_apps"

        /** How many apps the row shows before its "See all" tile. */
        const val APPS = 11
    }

    companion object {
        /** The row of the section [id]: Favorites or a category. */
        fun ofSection(id: String): HomeRow = if (id == HomeSection.FAVORITES) Favorites else Category(id)
    }
}

data class HomeRowEntry(val row: HomeRow, val visible: Boolean)

/**
 * The rows in the user's [order]. A row it does not name yet (a new category, a row added in an
 * update) goes right after the row it follows in the default order, or first if none does: a new
 * category after the last category, Favorites, new to the order, first. A row is visible unless
 * [hidden], or for rows that start off ([HomeRow.defaultVisible]), only when [shown].
 */
fun arrangeRows(
    order: List<String>,
    hidden: Set<String>,
    categoryIds: List<String>,
    shown: Set<String> = emptySet(),
): List<HomeRowEntry> {
    val all = listOf(HomeRow.Favorites, HomeRow.WatchNext, HomeRow.Opened, HomeRow.Frequent, HomeRow.Recent) +
        categoryIds.map(HomeRow::Category) + HomeRow.Recommendations + HomeRow.AllApps
    val byKey = all.associateBy { it.key }
    val arranged = order.mapNotNull(byKey::get).distinct().toMutableList()
    all.forEachIndexed { index, row ->
        if (row in arranged) return@forEachIndexed
        val after = all.subList(0, index).lastOrNull { it in arranged }
        arranged.add(after?.let { arranged.indexOf(it) + 1 } ?: 0, row)
    }
    return arranged.map { row ->
        HomeRowEntry(row, if (row.defaultVisible) row.key !in hidden else row.key in shown)
    }
}

/** The order after moving [key] [by] places; the result names every current row. */
fun List<HomeRowEntry>.movedRow(key: String, by: Int): List<String> {
    val keys = map { it.row.key }
    return keys.moved(keys.indexOf(key), by) ?: keys
}
