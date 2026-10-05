package dev.lumbina.tv.core.domain.text

/** One way of putting recognized text where the user wants it. */
interface TextSink {
    /** Short name for logs. */
    val name: String

    /** Returns true if the text was delivered. */
    suspend fun deliver(text: String): Boolean
}

/** Searches YouTube in the app in use for it ([YouTube]); false when there is none or it didn't open. */
fun interface YouTubeSearch {
    suspend fun search(query: String): Boolean
}

/** Tries sinks in order; returns the name of the sink that took the text, or null. */
class DeliverText(private val sinks: List<TextSink>) {
    suspend operator fun invoke(text: String): String? = sinks.firstOrNull { it.deliver(text) }?.name
}

/** The field that has input focus. */
class FocusedFieldSink(private val screen: ScreenReader) : TextSink {
    override val name = "focused field"

    override suspend fun deliver(text: String): Boolean {
        val field = screen.focusedInput()?.takeIf { it.isEditable } ?: return false
        return field.fill(text)
    }
}

/**
 * The one visible text field: TV apps often keep focus on their own keyboard, not on the field.
 * With several (login and password, filters) there is no telling which one was meant.
 */
class AnyFieldSink(private val screen: ScreenReader) : TextSink {
    override val name = "visible field"

    override suspend fun deliver(text: String): Boolean {
        val fields = screen.activeWindow()?.walk()?.filter { it.isEditable && it.isVisible }?.take(2)?.toList()
        val field = fields?.singleOrNull() ?: return false
        return field.fill(text)
    }
}

/**
 * A search in [search] — only while a launcher is in front ([launcherPackages]); inside
 * other apps jumping away would be surprising.
 */
class LauncherSearchSink(
    private val screen: ScreenReader,
    private val launcherPackages: Set<String>,
    private val search: YouTubeSearch,
) : TextSink {
    override val name = "launcher search"

    override suspend fun deliver(text: String): Boolean {
        val pkg = screen.activeWindow()?.packageName ?: return false
        return pkg in launcherPackages && search.search(text)
    }
}

/** Types [text]; searching right away is what TV fields want, but a message is left for the user to send. */
private fun UiNode.fill(text: String): Boolean {
    if (!setText(text)) return false
    if (!sendsOnSubmit) submit()
    return true
}
