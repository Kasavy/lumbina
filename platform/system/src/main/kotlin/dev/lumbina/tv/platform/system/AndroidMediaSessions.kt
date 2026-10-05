package dev.lumbina.tv.platform.system

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.status.MediaSessions
import dev.lumbina.tv.core.domain.status.NowPlaying
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Other apps' media sessions, readable because Lumbina's notification listener is allowed. The one
 * shown is the first playing session, else the first paused one (sessions come most recent first).
 */
internal class AndroidMediaSessions @Inject constructor(@ApplicationContext private val context: Context) :
    MediaSessions {

    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, LumbinaNotificationListener::class.java)
    private val handler = Handler(Looper.getMainLooper())

    override fun nowPlaying(): Flow<NowPlaying?> = callbackFlow {
        var watched = emptyList<MediaController>()
        val callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                trySend(current(watched))
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                trySend(current(watched))
            }
        }
        val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            watched.forEach { it.unregisterCallback(callback) }
            watched = controllers.orEmpty()
            watched.forEach { it.registerCallback(callback, handler) }
            trySend(current(watched))
        }
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            sessionsChanged.onActiveSessionsChanged(manager.getActiveSessions(listener))
        } catch (_: SecurityException) {
            trySend(null) // no notification access: nothing to show
        }
        awaitClose {
            runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
            watched.forEach { it.unregisterCallback(callback) }
        }
    }.distinctUntilChanged()

    override fun playPause() {
        val controller = controller() ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    override fun next() {
        controller()?.transportControls?.skipToNext()
    }

    override fun previous() {
        controller()?.transportControls?.skipToPrevious()
    }

    private fun controller(): MediaController? = runCatching { manager.getActiveSessions(listener) }.getOrNull()
        ?.let(::pick)

    private fun current(controllers: List<MediaController>): NowPlaying? {
        val controller = pick(controllers) ?: return null
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.takeIf { it.isNotBlank() }
            ?: return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
        return NowPlaying(title, artist?.takeIf { it.isNotBlank() }, controller.packageName, playing)
    }

    private fun pick(controllers: List<MediaController>): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
}
