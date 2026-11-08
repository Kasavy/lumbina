package dev.lumbina.tv.platform.accessibility

/**
 * A press that acts only when made again soon after it asked: the first press asks, and the same
 * again within [windowMillis] of the question appearing ([asked]) confirms. A press while the
 * first is still being worked out asks again rather than confirming what wasn't seen.
 */
internal class ConfirmByRepeat(private val windowMillis: Long, private val now: () -> Long) {
    private var askedAt: Long? = null

    /** Whether this press confirms; a confirming press uses the question up. */
    fun confirms(): Boolean {
        val at = askedAt ?: return false
        val confirmed = now() - at <= windowMillis
        askedAt = null
        return confirmed
    }

    /** The question is on screen now: the window starts. */
    fun asked() {
        askedAt = now()
    }

    /** The question no longer stands (it could not be acted on). */
    fun forget() {
        askedAt = null
    }
}
