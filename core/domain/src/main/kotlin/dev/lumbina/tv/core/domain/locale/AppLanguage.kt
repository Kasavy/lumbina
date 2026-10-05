package dev.lumbina.tv.core.domain.locale

/** Lumbina's own interface language, apart from the device's (e.g. Russian on an English system). */
interface AppLanguage {
    /** False where Android cannot set a language per app (before Android 13). */
    val supported: Boolean

    /** A language tag such as "ru", or null for the device's language. */
    fun current(): String?

    fun set(tag: String?)

    companion object {
        val CHOICES = listOf(null, "ru", "en")
    }
}
