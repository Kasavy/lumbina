package dev.lumbina.tv.platform.accessibility

import android.view.KeyEvent

/**
 * A key event as the system tells it apart from every other: the same press, delivered again,
 * has all of these the same; a repeat or the release differs in its time, count or action.
 */
internal data class KeyStamp(
    val deviceId: Int,
    val keyCode: Int,
    val scanCode: Int,
    val action: Int,
    val repeatCount: Int,
    val downTime: Long,
    val eventTime: Long,
) {
    companion object {
        fun of(event: KeyEvent) = KeyStamp(
            event.deviceId,
            event.keyCode,
            event.scanCode,
            event.action,
            event.repeatCount,
            event.downTime,
            event.eventTime,
        )
    }
}

/**
 * A key event handed over a second time gets the answer it got the first time and does nothing
 * more; [onEcho] hears of the first such event. Meant for a service bound twice (the list of
 * services rewritten by another app or the firmware while the system was binding ours), where the
 * system would hand each event to both connections and every press act twice; not seen to happen
 * on the projector, the guard costs next to nothing.
 */
internal class KeyEcho(private val onEcho: (KeyStamp) -> Unit = {}, private val remembered: Int = REMEMBERED) {
    private val answers = ArrayDeque<Pair<KeyStamp, Boolean>>()
    private var heard = false

    fun answer(stamp: KeyStamp, handle: () -> Boolean): Boolean {
        answers.firstOrNull { it.first == stamp }?.let { (_, answer) ->
            if (!heard) {
                heard = true
                onEcho(stamp)
            }
            return answer
        }
        val answer = handle()
        answers.addLast(stamp to answer)
        if (answers.size > remembered) answers.removeFirst()
        return answer
    }

    private companion object {
        /** A press and its release, with a few events of other keys between the two deliveries. */
        const val REMEMBERED = 8
    }
}
