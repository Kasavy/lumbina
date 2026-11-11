package dev.lumbina.tv.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * A mouse, or an air mouse pointing at the screen, clicks what is under its pointer.
 *
 * tv-material's cards, buttons and list items take OK from the D-pad only. Here the button is read
 * before anything inside sees it (the initial pass) and taken from them, so nothing clicks twice.
 * What is under the pointer is found as accessibility finds it, by the action "click" in the
 * semantics; it is focused and clicked when the button is let go over it, or long-clicked once held
 * as long as a long press, as OK does. Disabled, it is only focused. Where nothing clicks, the
 * press goes on to what is there.
 *
 * Moving the pointer leaves the focus alone: a list scrolls to its focused row, which would then
 * slide under a following pointer and scroll it on. A press within a double click of the last
 * click and where it was, in another window or on anything but what that click clicked, is taken
 * and does nothing:
 * it is the second half of a double click on what opened a window or a page, and would click
 * whatever of it happens to be under the pointer, or close it as a click beside its panel.
 * The mouse in use is told ([ProvideMouseClicks]), here in every window.
 *
 * Touch is left as it is. It goes on the root of every window, as each dialog and popup has
 * semantics of its own: [LumbinaDialog] and [LumbinaPopup] put it there.
 */
@Composable
fun Modifier.mouseClicks(): Modifier {
    // Compose's view is a RootForTest: the only public way to a window's semantics.
    val root = LocalView.current as? RootForTest ?: return this
    val inputMode = LocalInputModeManager.current
    val timing = LocalViewConfiguration.current.let {
        Timing(it.longPressTimeoutMillis, it.doubleTapTimeoutMillis, it.touchSlop * DOUBLE_CLICK_SLOPS)
    }
    val place = remember { Place() }
    // Without ProvideMouseClicks around, this window knows of its own clicks only.
    val clicks = LocalMouseClicks.current ?: remember { MouseClickTime() }
    return onPlaced { place.coordinates = it }
        .pointerInput(root, inputMode, timing, clicks) {
            awaitPointerEventScope {
                val window = Window(root, place, clicks)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.type in MOUSE } ?: continue
                    clicks.onPointer()
                    if (event.type == PointerEventType.Press &&
                        event.mainButton
                    ) {
                        pressed(change, window, inputMode, timing)
                    }
                }
            }
        }
}

/** [doubleSlop]: how far from the last click, in px, a press can still be the second half of a double click. */
internal data class Timing(val longPressMillis: Long, val doubleTapMillis: Long, val doubleSlop: Float)

/** A hand's double click lands a little off its first half: an air mouse's more than a desk mouse's. */
private const val DOUBLE_CLICK_SLOPS = 3

/**
 * When a mouse last clicked anything, and what, in any of the windows sharing this, by the events'
 * clock; read and written on the main thread, where all of Lumbina's windows take their input.
 * [onPointer]: the mouse is in use in one of them.
 */
class MouseClickTime(internal val onPointer: () -> Unit = {}) {
    private var last = Long.MIN_VALUE / 2
    private var lastAt: Offset? = null
    private var lastRoot: RootForTest? = null
    private var lastNode = 0

    /** A click at [millis], at [onScreen], on [node] of [root]. */
    internal fun clicked(millis: Long, onScreen: Offset?, root: RootForTest, node: Int) {
        last = millis
        lastAt = onScreen
        lastRoot = root
        lastNode = node
    }

    /**
     * A press at [millis] and [onScreen] on [target] in [root] is the second half of a double click
     * whose first clicked something else: what it opened is under the pointer now. On the same, it
     * is a click; so is one away from the last ([Timing.doubleSlop]), a quick click on the next button.
     */
    internal fun doubles(millis: Long, onScreen: Offset?, root: RootForTest, target: SemanticsNode?, timing: Timing) =
        millis - last < timing.doubleTapMillis &&
            onScreen != null && lastAt?.let { (it - onScreen).getDistance() < timing.doubleSlop } == true &&
            (root !== lastRoot || target?.id != lastNode)
}

// A local: a dialog or popup is a composition of its own, and only locals reach it from where it was opened.
@Suppress("CompositionLocalAllowlist")
private val LocalMouseClicks = staticCompositionLocalOf<MouseClickTime?> { null }

/**
 * [content] with one [MouseClickTime] for all its windows: a dialog or popup takes the locals of
 * the place that opened it, so it sees a click made there. [onPointer]: a mouse is in use in one of
 * them, moved, turned or pressed; a dialog's or popup's input doesn't pass the activity.
 */
@Composable
fun ProvideMouseClicks(onPointer: () -> Unit = {}, content: @Composable () -> Unit) {
    val clicks = remember(onPointer) { MouseClickTime(onPointer) }
    CompositionLocalProvider(LocalMouseClicks provides clicks, content = content)
}

/** A window's root, as [mouseClicks] reads it, with the time of the last click it shares. */
private class Window(val root: RootForTest, val place: Place, val clicks: MouseClickTime) {
    /**
     * What was [pressed], as it is now, if it is still there and [at] is on it, where it is now or
     * where it was at the press. A list may have put another item at the place meanwhile, which is
     * not clicked; or moved the pressed one away: before Android 13 the press puts the window in touch
     * mode, the focus leaves its row and the list scrolls to where it went.
     */
    fun still(pressed: Pressed, at: Offset): Pressed? {
        val now = root.semanticsOwner.rootSemanticsNode.withId(pressed.node.id) ?: return null
        return pressed.on(now).takeIf { now.boundsInRoot.contains(at) || pressed.pressedAt.contains(at) }
    }
}

private fun SemanticsNode.withId(id: Int): SemanticsNode? =
    takeIf { it.id == id } ?: children.firstNotNullOfOrNull { it.withId(id) }

private fun RootForTest.clickableAt(at: Offset) = semanticsOwner.rootSemanticsNode.clickableAt(at)

/** The main button pressed: on what clicks, followed to its release; the second half of a double click, taken. */
private suspend fun AwaitPointerEventScope.pressed(
    press: PointerInputChange,
    window: Window,
    inputMode: InputModeManager,
    timing: Timing,
) {
    val at = window.place.inRoot(press.position) ?: return
    val target = window.root.clickableAt(at)
    val onScreen = window.place.onScreen(press.position)
    if (window.clicks.doubles(press.uptimeMillis, onScreen, window.root, target, timing)) {
        press.consume()
        awaitRelease(press.id)
    } else if (target != null) {
        press.consume()
        follow(press, at, Pressed(target, inputMode), window, timing.longPressMillis)
    }
}

/** From the [press] on [pressed], at [at], to its release, taken from the components all along. */
private suspend fun AwaitPointerEventScope.follow(
    press: PointerInputChange,
    at: Offset,
    pressed: Pressed,
    window: Window,
    longPressMillis: Long,
) {
    val early = withTimeoutOrNull(longPressMillis) { awaitRelease(press.id) }
    if (early == null && window.still(pressed, at)?.long() == true) {
        // Done while held, as with OK; the release is the long click's.
        val release = awaitRelease(press.id)
        window.clicks.clicked(release.millis, window.place.onScreen(release.position), window.root, pressed.node.id)
        return
    }
    val release = early ?: awaitRelease(press.id)
    // Cancelled (the stream was taken, or the mouse went), or let go elsewhere: a change of mind.
    if (release.cancelled) return
    val clicked = window.place.inRoot(release.position)?.let { window.still(pressed, it) } ?: return
    window.clicks.clicked(release.millis, window.place.onScreen(release.position), window.root, pressed.node.id)
    clicked.click()
}

/** A mouse, or a pointer of no named kind (some devices, `input mouse tap` on Android 8). */
private val MOUSE = setOf(PointerType.Mouse, PointerType.Unknown)

/** The main button, or one the device doesn't name (`input mouse tap`, some air mice). */
private val PointerEvent.mainButton get() = !buttons.isSecondaryPressed && !buttons.isTertiaryPressed

private class Release(val position: Offset, val millis: Long, val cancelled: Boolean)

/**
 * The pointer [id] let go; taken from the components like its press. A press of another pointer
 * first counts as a cancel: this one's release was lost on the way.
 */
private suspend fun AwaitPointerEventScope.awaitRelease(id: PointerId): Release {
    while (true) {
        val changes = awaitPointerEvent(PointerEventPass.Initial).changes
        changes.firstOrNull { it.id != id && it.pressed && !it.previousPressed }
            ?.let { return Release(it.position, it.uptimeMillis, cancelled = true) }
        val change = changes.firstOrNull { it.id == id }
        if (change == null || change.pressed) continue
        // A cancel comes as a release already consumed; a real one is first seen here.
        val release = Release(change.position, change.uptimeMillis, cancelled = change.isConsumed)
        change.consume()
        return release
    }
}

/** Where the modified root is in its window's root, for the semantics' positions. */
private class Place {
    var coordinates: LayoutCoordinates? = null

    fun inRoot(position: Offset): Offset? = coordinates?.takeIf { it.isAttached }?.localToRoot(position)

    /** On the screen: windows' roots don't share an origin, a double click's halves may fall in two. */
    fun onScreen(position: Offset): Offset? = coordinates?.takeIf { it.isAttached }?.localToScreen(position)
}

/** What the pointer pressed on, done as OK would. */
private class Pressed(
    val node: SemanticsNode,
    private val inputMode: InputModeManager,
    /** Where it was on the window at the press. */
    val pressedAt: Rect = node.boundsInRoot,
) {
    private val enabled = SemanticsProperties.Disabled !in node.config

    /** The same, as it is [now]: its actions as they are now, not as they were at the press. */
    fun on(now: SemanticsNode) = Pressed(now, inputMode, pressedAt)

    fun click() {
        focus()
        if (enabled) node.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()
    }

    /**
     * False where it has none: tv-material lists the action on every component, and it then
     * answers false, so the release clicks instead.
     */
    fun long(): Boolean {
        val action = node.config.getOrNull(SemanticsActions.OnLongClick)?.action ?: return false
        if (!enabled) return false
        focus()
        return action()
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun focus() {
        // A pointer can put the window in touch mode, where a focus asked for is soon dropped.
        inputMode.requestInputMode(InputMode.Keyboard)
        node.config.getOrNull(SemanticsActions.RequestFocus)?.action?.invoke()
    }
}

/** The deepest node at [at] that clicks; of overlapping ones, the one drawn last, on top. */
internal fun SemanticsNode.clickableAt(at: Offset): SemanticsNode? {
    for (child in children.asReversed()) child.clickableAt(at)?.let { return it }
    return takeIf { SemanticsActions.OnClick in config && boundsInRoot.contains(at) }
}

/** [content] as a window's root, with [mouseClicks]. */
@Composable
internal fun MouseClicks(content: @Composable () -> Unit) {
    Box(Modifier.mouseClicks(), propagateMinConstraints = true) { content() }
}
