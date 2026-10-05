package dev.lumbina.tv.core.domain.text

/** Where the home screen's search leads. */
enum class SearchTarget {
    /** The device's own search (Google TV, Android TV): Lumbina only opens it. */
    System,

    /** YouTube in the app chosen for it ([YouTube]), with Lumbina's keyboard for the query. */
    YouTube,
}

/** What search the device offers; no search at all hides the button. */
interface HomeSearch {
    /** The system's search if there is one, else YouTube if an app for it is installed, else null. */
    suspend fun target(): SearchTarget?

    /** Opens the system's search; false if there is none. */
    fun openSystem(): Boolean
}
