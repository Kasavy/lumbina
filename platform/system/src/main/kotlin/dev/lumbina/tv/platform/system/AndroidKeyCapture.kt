package dev.lumbina.tv.platform.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.domain.remote.KeyCapture
import dev.lumbina.tv.core.domain.remote.KeyGesture
import dev.lumbina.tv.core.domain.remote.RemoteKey
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Asks the remote's service, by broadcasts within the app, for the buttons pressed while collected. */
internal class AndroidKeyCapture @Inject constructor(@ApplicationContext private val context: Context) : KeyCapture {

    override fun keys(): Flow<RemoteKey> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getStringExtra(LumbinaActions.EXTRA_KEY)?.let { trySend(RemoteKey(it)) }
            }
        }
        val filter = IntentFilter(LumbinaActions.KEY_CAPTURED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ask(on = true)
        awaitClose {
            ask(on = false)
            context.unregisterReceiver(receiver)
        }
    }

    override fun gestures(key: RemoteKey): Flow<KeyGesture> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val name = intent.getStringExtra(LumbinaActions.EXTRA_GESTURE)
                KeyGesture.entries.firstOrNull { it.name == name }?.let { trySend(it) }
            }
        }
        val filter = IntentFilter(LumbinaActions.KEY_GESTURE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        probe(key)
        awaitClose {
            probe(null)
            context.unregisterReceiver(receiver)
        }
    }

    private fun probe(key: RemoteKey?) {
        val intent = Intent(LumbinaActions.PROBE_KEY).setPackage(context.packageName)
        key?.let { intent.putExtra(LumbinaActions.EXTRA_KEY, it.id) }
        context.sendBroadcast(intent)
    }

    private fun ask(on: Boolean) {
        val intent = Intent(LumbinaActions.CAPTURE_KEY)
            .setPackage(context.packageName)
            .putExtra(LumbinaActions.EXTRA_CAPTURE_ON, on)
        context.sendBroadcast(intent)
    }
}
