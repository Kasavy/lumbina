package dev.lumbina.tv.vendor.hisense

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import dagger.hilt.android.qualifiers.ApplicationContext
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

/** Hisense's inputs. */
class HisenseInputs @Inject constructor(@ApplicationContext private val context: Context) : InputSources {

    /** One call at a time, in the order asked. */
    private val calls = Mutex()

    /** False also where it can't be told. */
    override suspend fun showAndroid(): Boolean = withService { service ->
        service.call(GET_CURRENT) != ANDROID && service.call(SET_CURRENT, ANDROID) != 0
    } ?: false

    override suspend fun list(): List<InputSource> = withService { service ->
        service.ints(GET_INPUTS).filter { it / TYPE != OWN_TYPE }.map { id ->
            InputSource(id.toString(), spacedName(service.text(GET_NAME, id).orEmpty().ifBlank { id.toString() }))
        }
    }.orEmpty()

    override suspend fun current(): String? =
        withService { service -> service.call(GET_CURRENT).takeIf { it != ANDROID }?.toString() }

    /** False also where there is nothing to show it on: then the input stays as it was. */
    override suspend fun open(id: String): Boolean {
        val source = id.toIntOrNull() ?: return false
        val screen = Intent(Intent.ACTION_MAIN).setComponent(LIVE_TV).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(screen, 0) == null) return false
        if (withService { it.call(SET_CURRENT, source) != 0 } != true) return false
        if (runCatching { context.startActivity(screen) }.isSuccess) return true
        // Switched, with nothing to show it: Android's picture again.
        showAndroid()
        return false
    }

    override fun showsInputs(packageName: String): Boolean = packageName == LIVE_TV.packageName

    /** [block] with the service bound, off the main thread; null where it can't be bound. */
    private suspend fun <T> withService(block: (IBinder) -> T): T? = calls.withLock {
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
                    val intent = Intent().setComponent(SERVICE)
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

    private companion object {
        val SERVICE = ComponentName("com.jamdeo.tv.service", "com.jamdeo.tv.service.SourceService")
        const val DESCRIPTOR = "com.jamdeo.tv.service.ISourceRemoteService"
        const val SET_CURRENT = 1
        const val GET_CURRENT = 4
        const val GET_NAME = 11
        const val GET_INPUTS = 12
        const val ANDROID = 2562
        const val TYPE = 256

        const val OWN_TYPE = 10
        val LIVE_TV = ComponentName("com.jamdeo.tv.livetv", "com.jamdeo.tv.livetv.LiveTvActivity")
        const val BIND_MILLIS = 3_000L
    }
}

/** "HDMI1" as "HDMI 1"; a name with its space already stays as it is. */
internal fun spacedName(name: String): String = name.replace(Regex("""(?<=[^\d\s])(\d+)$"""), " $1")
