package dev.lumbina.tv.vendor.generic

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.device.InputPresence
import dev.lumbina.tv.core.domain.device.InputSource
import dev.lumbina.tv.core.domain.device.InputSources
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The inputs Android itself knows of: on a TV whose firmware lists its HDMI and other inputs with
 * Android's TV inputs, as most Android TV sets do. An input is shown as Android shows one, by the
 * TV's own app; leaving that app leaves the input, so there is no picture of Android's to bring back.
 * A device on a port that speaks HDMI-CEC has an input of its own under the port's, named as the
 * device calls itself: not a second input to show, but what is on the port.
 */
internal class SystemInputs @Inject constructor(@ApplicationContext private val context: Context) : InputSources {

    private val manager: TvInputManager? get() = context.getSystemService(TvInputManager::class.java)

    /** Whether the device has a TV's features at all; whether it has inputs is told by [list]. */
    val isTv: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)

    override suspend fun showAndroid(): Boolean = false

    override suspend fun list(): List<InputSource> = withContext(Dispatchers.IO) {
        inputs().map { InputSource(it.id, it.name()) }
    }

    override suspend fun presence(): Map<String, InputPresence> = withContext(Dispatchers.IO) {
        val manager = manager ?: return@withContext emptyMap()
        val all = passthrough()
        val ports = all.filter { it.parentId == null }.mapNotNull { port -> manager.state(port.id)?.let { port to it } }
        signals = signals.learn(ports.map { it.second })
        ports.mapNotNull { (port, state) ->
            val devices = all.filter { it.parentId == port.id }
                .mapNotNull { device -> manager.state(device.id)?.let { device.name() to it } }
            portPresence(state, devices, signals)?.let { port.id to it }
        }.toMap()
    }

    /** What this TV's port states have shown they mean so far: learnt once, kept while the process lives. */
    @Volatile private var signals = PortSignals.None

    /** Not told: Android does not say which input the TV's app shows. */
    override suspend fun current(): String? = null

    override suspend fun open(id: String): Boolean = runCatching { context.startActivity(viewIntent(id)) }.isSuccess

    override fun showsInputs(packageName: String): Boolean = packageName == viewer()

    /** Known once found: the TV's app that shows an input, what the system opens for one. */
    private var viewerFound: String? = null

    /** When it was last looked for in vain: asked of the system again only a while later. */
    private var missedAt = 0L

    /** Not the system's own chooser, which shows when several apps could show an input. */
    private fun viewer(): String? {
        viewerFound?.let { return it }
        val now = SystemClock.elapsedRealtime()
        if (missedAt != 0L && now - missedAt < ASK_AGAIN_MILLIS) return null
        val found = inputs().firstOrNull()?.let { any ->
            context.packageManager.resolveActivity(viewIntent(any.id), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.takeIf { it != ANDROID }
        }
        if (found == null) missedAt = now else viewerFound = found
        return found
    }

    /**
     * The ports a picture comes in through (HDMI and the like, not the tuner), as the user did not
     * hide them; not the inputs of the devices on them.
     */
    private fun inputs(): List<TvInputInfo> = passthrough().filter { it.parentId == null }

    private fun passthrough(): List<TvInputInfo> = runCatching {
        manager?.tvInputList.orEmpty().filter { it.isPassthroughInput && !it.isHidden(context) }
    }.getOrDefault(emptyList())

    /** The name the user gave the input in the TV's settings, else the TV's own. */
    private fun TvInputInfo.name(): String =
        loadCustomLabel(context)?.toString()?.takeIf { it.isNotBlank() } ?: loadLabel(context).toString()

    private fun viewIntent(id: String): Intent =
        Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val ANDROID = "android"
        const val ASK_AGAIN_MILLIS = 60_000L
    }
}

/** What Android says is on input [id]; null where it won't say. */
internal fun TvInputManager.state(id: String): Int? = runCatching { getInputState(id) }.getOrNull()

/**
 * What a TV's port states mean, as far as they have shown it. Android counts a port connected
 * when its TV service never reports one, so on many TVs "connected" says nothing ([None]). A TV
 * that has said "disconnected" of a port tells cables ([Cables]): connected is a cable, standby a
 * cable too. One that never says disconnected but has shown ports connected and in standby side
 * by side tells by the port's signal ([Signal]): Android puts an HDMI port with no hotplug signal
 * in standby, "unknown", which on such TVs is a port with nothing on it (TCL's own launcher leaves
 * those out of its inputs).
 */
internal enum class PortSignals {
    None,
    Signal,
    Cables,
    ;

    /** What the ports' [states] now add to what was known. */
    fun learn(states: List<Int>): PortSignals = when {
        this == Cables || TvInputManager.INPUT_STATE_DISCONNECTED in states -> Cables

        TvInputManager.INPUT_STATE_CONNECTED in states && TvInputManager.INPUT_STATE_CONNECTED_STANDBY in states ->
            Signal

        else -> this
    }
}

/**
 * What is on a port, from its [state] and its [devices]' (name and state, as HDMI-CEC tells them):
 * a device that is on is working, named; one asleep is only connected. Without a device of its
 * own the port is empty when Android says disconnected, or in standby where the TV tells by the
 * port's signal; connected only where the TV's states mean something ([signals]; null: not told).
 */
internal fun portPresence(state: Int, devices: List<Pair<String, Int>>, signals: PortSignals): InputPresence? {
    devices.firstOrNull { it.second == TvInputManager.INPUT_STATE_CONNECTED }?.let {
        return InputPresence.Working(it.first.trim().takeIf(String::isNotEmpty))
    }
    val noDevice = devices.all { it.second == TvInputManager.INPUT_STATE_DISCONNECTED }
    val nothing = noDevice &&
        (
            state == TvInputManager.INPUT_STATE_DISCONNECTED ||
                (signals == PortSignals.Signal && state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY)
            )
    return when {
        nothing -> InputPresence.Empty

        // An HDMI-CEC device that answers, asleep: there is something on the port.
        devices.any { it.second == TvInputManager.INPUT_STATE_CONNECTED_STANDBY } -> InputPresence.Connected

        signals != PortSignals.None -> InputPresence.Connected

        else -> null
    }
}
