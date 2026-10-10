package dev.lumbina.tv.core.domain.text

/** One way of putting recognized text where the user wants it. */
interface TextSink {
    /** Short name for logs. */
    val name: String

    /** Returns true if the text was delivered. */
    suspend fun deliver(text: String): Boolean

    /** As [deliver], for a sink that can tell the text was its to take and it could not. */
    suspend fun take(text: String): Taking = if (deliver(text)) Taking.Taken else Taking.NotMine
}

/** What a sink did with a text; after [Failed] no other sink is tried. */
enum class Taking { Taken, NotMine, Failed }

/** Where a text went: to a sink, nowhere, or to a sink that could not take it. */
sealed interface Delivery {
    data class To(val sink: String) : Delivery

    data object Nowhere : Delivery

    data class Failed(val sink: String) : Delivery
}

/** Searches YouTube in the app in use for it ([YouTube]); false when there is none or it didn't open. */
fun interface YouTubeSearch {
    suspend fun search(query: String): Boolean
}

/** Tries sinks in order. */
class DeliverText(private val sinks: List<TextSink>) {
    /** The name of the sink that took the text, or null. */
    suspend operator fun invoke(text: String): String? = (deliver(text) as? Delivery.To)?.sink

    suspend fun deliver(text: String): Delivery {
        for (sink in sinks) {
            when (sink.take(text)) {
                Taking.Taken -> return Delivery.To(sink.name)
                Taking.Failed -> return Delivery.Failed(sink.name)
                Taking.NotMine -> Unit
            }
        }
        return Delivery.Nowhere
    }
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
 * other apps jumping away would be surprising. A search that did not open is a failure to
 * tell the user of, not a text with nowhere to go.
 */
class LauncherSearchSink(
    private val screen: ScreenReader,
    private val launcherPackages: Set<String>,
    private val search: suspend (String) -> Boolean,
) : TextSink {
    override val name = "launcher search"

    override suspend fun deliver(text: String): Boolean = take(text) == Taking.Taken

    override suspend fun take(text: String): Taking = when {
        screen.activeWindow()?.packageName !in launcherPackages -> Taking.NotMine
        search(text) -> Taking.Taken
        else -> Taking.Failed
    }
}

/** Types [text]; searching right away is what TV fields want, but a message is left for the user to send. */
private fun UiNode.fill(text: String): Boolean {
    if (!setText(text)) return false
    if (!sendsOnSubmit) submit()
    return true
}
