package dev.lumbina.tv.vendor.hisense

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.device.DeviceAnswer
import dev.lumbina.tv.core.domain.device.InputPresence
import dev.lumbina.tv.core.domain.device.InputSource
import dev.lumbina.tv.core.domain.device.InputSources
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** Hisense's inputs. */
class HisenseInputs @Inject constructor(@ApplicationContext private val context: Context) : InputSources {

    /** One call at a time, in the order asked. */
    private val calls = Mutex()

    /** Read once, off the main thread (the first call is from a service binding's block). */
    private val codes: JamdeoCalls by lazy(::firmwareCalls)

    /** False also where it can't be told. */
    override suspend fun showAndroid(): Boolean = withService { service ->
        service.call(codes.getCurrent) != ANDROID && service.call(codes.setCurrent, ANDROID) != 0
    } ?: false

    override suspend fun list(): List<InputSource> = withService { service ->
        service.inputIds().map { id ->
            InputSource(id.toString(), spacedName(service.text(codes.getName, id).orEmpty().ifBlank { id.toString() }))
        }
    }.orEmpty()

    override suspend fun current(): String? =
        withService { service -> service.call(codes.getCurrent).takeIf { it != ANDROID }?.toString() }

    /** False also where there is nothing to show it on: then the input stays as it was. */
    override suspend fun open(id: String): Boolean {
        val source = id.toIntOrNull() ?: return false
        val screen = Intent(Intent.ACTION_MAIN).setComponent(LIVE_TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(screen, 0) == null) return false
        if (withService { it.call(codes.setCurrent, source) != 0 } != true) return false
        if (runCatching { context.startActivity(screen) }.isSuccess) return true
        // Switched, with nothing to show it: Android's picture again.
        showAndroid()
        return false
    }

    override fun showsInputs(packageName: String): Boolean = packageName == LIVE_TV.packageName

    /** The inputs, without the device's own screens (Android's picture among them). */
    private fun IBinder.inputIds(): List<Int> = ints(codes.getInputs).filter { it / TYPE != OWN_TYPE }

    /**
     * A cable is told by the input service; a working device by the name and kind it sends with its
     * picture (HDMI's SPD), which the system service gives for any input, chosen or not, and which a
     * sleeping device stops sending. Where the system service is not there, the cable alone.
     */
    override suspend fun presence(): Map<String, InputPresence> {
        // The cables asked apart: a model whose call differs still gets the names below.
        val (ids, cabled) = withService { service ->
            service.inputIds() to
                runCatching { service.ints(codes.getConnected).toSet() }.getOrNull()
        } ?: return emptyMap()
        val devices = withService(SYSTEM_SERVICE) { system ->
            ids.associateWith { system.device(it, codes.invokeTvMethod) }
        }.orEmpty()
        return ids.mapNotNull { id ->
            val device = devices[id]
            presenceOf(cabled?.let { id in it }, device?.first, device?.second)?.let { id.toString() to it }
        }.toMap()
    }

    /**
     * For a report: what the input service answers, as it answers it. Some models name an input
     * "0"; the reply's own words tell a name that is "0" from a call that means something else there.
     */
    suspend fun answers(): List<DeviceAnswer> = withService { service ->
        val ids = service.ints(codes.getInputs)
        listOf(
            DeviceAnswer("input service: interface", service.interfaceDescriptor.orEmpty()),
            DeviceAnswer("input service: calls", codes.said()),
            DeviceAnswer("input service: inputs (call ${codes.getInputs})", ids.joinToString()),
        ) + ids.map { id ->
            val answer = runCatching { service.ask(codes.getName, id) { rawReply(this) } }.getOrElse { "failed: $it" }
            DeviceAnswer("input service: name of $id (call ${codes.getName})", answer)
        }
    }.orEmpty().ifEmpty { listOf(DeviceAnswer("input service", "not reached")) } +
        DeviceAnswer("inputs now", presence().entries.joinToString { (id, presence) -> "$id: $presence" })

    /** [block] with [component] bound, off the main thread; null where it can't be bound. */
    private suspend fun <T> withService(component: ComponentName = SERVICE, block: (IBinder) -> T): T? =
        calls.withLock {
            var connection: ServiceConnection? = null
            return try {
                val service = withTimeoutOrNull(BIND_MILLIS) {
                    suspendCancellableCoroutine<IBinder?> { waiting ->
                        val bound = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                                if (waiting.isActive) waiting.resume(service)
                            }

                            override fun onServiceDisconnected(name: ComponentName) = Unit
                        }
                        connection = bound
                        val intent = Intent().setComponent(component)
                        if (!runCatching {
                                context.bindService(intent, bound, Context.BIND_AUTO_CREATE)
                            }.getOrDefault(false)
                        ) {
                            waiting.resume(null)
                        }
                    }
                } ?: return null
                withContext(Dispatchers.IO) { runCatching { block(service) }.getOrNull() }
            } finally {
                connection?.let { runCatching { context.unbindService(it) } }
            }
        }

    private companion object {
        val SERVICE = ComponentName("com.jamdeo.tv.service", "com.jamdeo.tv.service.SourceService")
        val SYSTEM_SERVICE = ComponentName("com.jamdeo.tv.service", "com.jamdeo.tv.service.SystemService")
        const val ANDROID = 2562
        const val TYPE = 256

        const val OWN_TYPE = 10
        val LIVE_TV = ComponentName("com.jamdeo.tv.livetv", "com.jamdeo.tv.livetv.LiveTvActivity")
        const val BIND_MILLIS = 3_000L
    }
}

/** One call of the service with an optional int argument, answered with an int. */
private fun IBinder.call(code: Int, argument: Int? = null): Int = ask(code, argument) { readInt() }

private fun IBinder.ints(code: Int): List<Int> = ask(code, null) { createIntArray()?.toList().orEmpty() }

private fun IBinder.text(code: Int, argument: Int): String? = ask(code, argument) { readString() }

private fun <T> IBinder.ask(code: Int, argument: Int?, read: Parcel.() -> T): T {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    try {
        data.writeInterfaceToken(DESCRIPTOR)
        argument?.let(data::writeInt)
        transact(code, data, reply, 0)
        reply.readException()
        return reply.read()
    } finally {
        reply.recycle()
        data.recycle()
    }
}

/** The name and kind the device on [input] sends; null where the service does not answer. */
private fun IBinder.device(input: Int, invoke: Int): Pair<String?, Int?>? = runCatching {
    val name = tvMethod(invoke, "hsTVgetSPDSourceName", input)?.optString("HdmiSourceName")
    val kind = tvMethod(invoke, "hsTVgetSPDSourceType", input)?.optInt("HdmiSourceType")
    name to kind
}.getOrNull()

/**
 * The system service's invokeTvMethod(name, {"SrcId": input}), as the firmware's own screens
 * call it; null unless it went well.
 */
private fun IBinder.tvMethod(invoke: Int, name: String, input: Int): JSONObject? {
    val data = Parcel.obtain()
    val reply = Parcel.obtain()
    try {
        data.writeInterfaceToken(SYSTEM_DESCRIPTOR)
        data.writeString(name)
        data.writeString(JSONObject().put("SrcId", input).toString())
        transact(invoke, data, reply, 0)
        reply.readException()
        return reply.readString()?.let(::JSONObject)?.takeIf { it.optInt("ret", -1) == 0 }
    } finally {
        reply.recycle()
        data.recycle()
    }
}

private const val DESCRIPTOR = "com.jamdeo.tv.service.ISourceRemoteService"
private const val SYSTEM_DESCRIPTOR = "com.jamdeo.tv.service.ISystemRemoteService"

/** The reply read as a string, then its words from the start as the firmware wrote them. */
private fun rawReply(reply: Parcel): String {
    val text = runCatching { reply.readString() }.getOrElse { "failed: $it" }
    reply.setDataPosition(0)
    val words = buildList { while (reply.dataAvail() >= Int.SIZE_BYTES && size < REPLY_WORDS) add(reply.readInt()) }
    return describeReply(text, reply.dataSize(), words)
}

private const val REPLY_WORDS = 32
private const val HEX_DIGITS = Int.SIZE_BITS / 4

/** How [rawReply] puts it: `"HDMI1" · 24 bytes: 00000000 00000005 …`. */
internal fun describeReply(text: String?, size: Int, words: List<Int>): String {
    val hex = words.joinToString(" ") { Integer.toHexString(it).padStart(HEX_DIGITS, '0') }
    return "${text?.let { "\"$it\"" } ?: "null"} · $size bytes: $hex"
}

/**
 * What is on an input: a device that sends its [name] or a [kind] other than 0 is working; else a
 * [cabled] one is only connected. Where the device was not asked ([name] and [kind] null), the cable
 * alone; null where the cable can't be told either.
 */
internal fun presenceOf(cabled: Boolean?, name: String?, kind: Int?): InputPresence? = when {
    !name.isNullOrBlank() -> InputPresence.Working(name.trim())
    kind != null && kind != 0 -> InputPresence.Working(null)
    cabled == true -> InputPresence.Connected
    cabled == false -> InputPresence.Empty
    else -> null
}

/** "HDMI1" as "HDMI 1"; a name with its space already stays as it is. */
internal fun spacedName(name: String): String = name.replace(Regex("""(?<=[^\d\s])(\d+)$"""), " $1")
