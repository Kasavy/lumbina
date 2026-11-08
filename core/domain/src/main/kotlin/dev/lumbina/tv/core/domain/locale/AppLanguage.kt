package dev.lumbina.tv.core.domain.locale

/**
 * Lumbina's own interface language, apart from the device's (e.g. Russian on an English system),
 * on every Android it runs on: the system keeps it from Android 13, Lumbina itself before.
 */
interface AppLanguage {
    /** A language tag such as "ru", or null for the device's language. */
    fun current(): String?

    fun set(tag: String?)

    companion object {
        val CHOICES = listOf(null, "ru", "en")
    }
}
