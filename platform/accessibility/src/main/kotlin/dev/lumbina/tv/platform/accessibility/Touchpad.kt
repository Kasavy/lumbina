package dev.lumbina.tv.platform.accessibility

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The phone remote's touchpad: the pointer ([cursor]) moved and clicked, and the two-finger swipe
 * in steps, the way the content goes ("down" when the fingers move up, as on a touchscreen). A
 * step goes to Lumbina's own overlays first ([overlay], on any Android); else, where the device
 * takes the service's arrows ([hasArrows], Android 13+), it is an arrow as the page's own ([arrow]):
 * lists on a TV follow the focus, and so do apps whose lists aren't in the accessibility tree (a
 * web page). Below 13 the list under the pointer is scrolled along that axis. Moves and clicks on
 * the main thread; a step from any, the [arrow] finding its own.
 */
internal class Touchpad(
    private val cursor: Pointer,
    private val hasArrows: Boolean,
    private val overlay: (String) -> Boolean,
    private val arrow: suspend (String) -> Boolean,
    private val main: CoroutineContext = Dispatchers.Main,
) {
    fun move(dx: Float, dy: Float) = cursor.move(dx, dy)

    fun click(long: Boolean) = cursor.click(long)

    fun hide() = cursor.hide()

    /** One step [dir] ("up", "down", "left", "right"); false for any other word or when nothing took it. */
    suspend fun scroll(dir: String?): Boolean {
        if (dir == null || dir !in DIRECTIONS) return false
        return when {
            withContext(main) { overlay(dir) } -> true
            !hasArrows -> withContext(main) { cursor.scroll(dir) }
            else -> arrow(dir)
        }
    }

    private companion object {
        val DIRECTIONS = setOf("up", "down", "left", "right")
    }
}

/** The touchpad's pointer on the screen ([PhoneCursor]). */
internal interface Pointer {
    fun move(dx: Float, dy: Float)

    fun click(long: Boolean): Boolean

    /** Scrolls the list under it one step [dir]; false when there is none. */
    fun scroll(dir: String): Boolean

    fun hide()
}
