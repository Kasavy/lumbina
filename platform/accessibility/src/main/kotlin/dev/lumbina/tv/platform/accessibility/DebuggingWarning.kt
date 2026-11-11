package dev.lumbina.tv.platform.accessibility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Debugging found off once the device is on, where Lumbina was connected to it ([Device.keepsService]: it
 * keeps the permission connecting gave it; on a TV where the connection is only an option, one who never
 * connected is not told): closing apps goes through it, on Vidda the remote's voice too
 * ([Device.voiceOverIt]), and a firmware may turn it off at a boot. A card in the corner says so: "Connect…"
 * opens "Access", where connecting tells what to turn on; "Not now" and Back put it away until debugging has
 * been seen on again ([dev.lumbina.tv.core.domain.access.AccessSettings.debuggingOffDismissed]), so one who
 * turned it off on purpose is told once. Any other key puts it away for now and goes on to what it does.
 * Lumbina doesn't turn debugging on itself: the device then forgets whom it let in, so connecting needs the
 * user anyway. Looked at when the service connects and each time the screen comes on. Main thread.
 */
internal class DebuggingWarning(
    private val context: Context,
    private val device: Device,
    private val settings: AccessSettingsRepository,
    private val card: PromptCard,
    private val log: LumbinaLog,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        // A button clicked is OK on it.
        card.takesKeys(::onKey)
    }

    /**
     * What the card asks of the device: whether Lumbina keeps the permission its setup over
     * debugging gave it, whether debugging is on, "Access" to open, and whether another of the
     * service's overlays has the keys now ([othersShowing]: the card waits for it to go, or it
     * would take that overlay's arrows and OK), and whether the remote's voice goes through
     * debugging too ([voiceOverIt], Vidda) or only closing apps does.
     */
    class Device(
        val keepsService: () -> Boolean,
        val debuggingOn: () -> Boolean,
        val openAccess: () -> Unit,
        val othersShowing: () -> Boolean = { false },
        val voiceOverIt: Boolean = true,
    ) {
        companion object {
            fun of(context: Context, keepsService: () -> Boolean, othersShowing: () -> Boolean, voiceOverIt: Boolean) =
                Device(
                    keepsService,
                    debuggingOn = {
                        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
                    },
                    openAccess = { context.openLumbina(LumbinaActions.SETTINGS_PAGE_ACCESS) },
                    othersShowing = othersShowing,
                    voiceOverIt = voiceOverIt,
                )
        }
    }

    private var scope: CoroutineScope? = null
    private var check: Job? = null
    private var showing = false

    /** The key the card took while showing: its repeats and its release are the card's too. */
    private var taken: Int? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = lookSoon()
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lookSoon()
    }

    fun stop() {
        context.unregisterReceiver(receiver)
        check?.cancel()
        hide()
    }

    /** Once debugging had time to come up by itself after a boot or a wake. */
    fun lookSoon() {
        check?.cancel()
        check = scope?.launch {
            delay(SETTLE_MILLIS)
            val (keeps, on) = withContext(io) { device.keepsService() to device.debuggingOn() }
            val dismissed = settings.settings.first().debuggingOffDismissed
            if (on) {
                // On again: the next time it is found off, the user is told again.
                if (dismissed) settings.update { it.copy(debuggingOffDismissed = false) }
                return@launch
            }
            if (!keeps || dismissed || showing) return@launch
            while (device.othersShowing()) delay(WAIT_MILLIS)
            log.log(TAG, "debugging is off")
            showing = card.show(
                context.getString(R.string.debugging_off),
                listOf(context.getString(R.string.debugging_connect), context.getString(R.string.debugging_not_now)),
                fraction = null,
                hint = context.getString(
                    if (device.voiceOverIt) R.string.debugging_off_hint else R.string.debugging_off_hint_apps,
                ),
            )
        }
    }

    /**
     * While the card shows: ◀ ▶ pick, OK presses, Back is "Not now"; any other key puts it away and
     * goes on. Only keys pressed while it shows are its own: one held from before, its repeats and
     * its release go on to the app under it.
     */
    fun onKey(event: KeyEvent): Boolean = when {
        event.action != KeyEvent.ACTION_DOWN -> releasedOwn(event)

        // A key held from before goes on to the app; one the card took, its repeats stay the card's.
        event.repeatCount > 0 -> event.keyCode == taken

        !showing -> false

        else -> pressed(event)
    }

    /** The release of the key the card took is the card's; any other goes on. */
    private fun releasedOwn(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP || event.keyCode != taken) return false
        taken = null
        return true
    }

    private fun pressed(event: KeyEvent): Boolean {
        taken = event.keyCode
        when {
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> card.move(-1)

            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> card.move(1)

            event.isOk || event.isBack -> answer(connect = event.isOk && card.selected == CONNECT)

            else -> {
                taken = null
                hide()
            }
        }
        return taken != null
    }

    private fun answer(connect: Boolean) {
        hide()
        if (connect) {
            device.openAccess()
        } else {
            scope?.launch { settings.update { it.copy(debuggingOffDismissed = true) } }
        }
    }

    private fun hide() {
        showing = false
        card.hide()
    }

    private companion object {
        const val TAG = "debugging"
        const val CONNECT = 0

        /** After a power-on: a firmware that turns debugging on by itself does it by then. */
        const val SETTLE_MILLIS = 15_000L

        /** How often it looks again whether the overlay with the keys has gone. */
        const val WAIT_MILLIS = 2_000L
    }
}
