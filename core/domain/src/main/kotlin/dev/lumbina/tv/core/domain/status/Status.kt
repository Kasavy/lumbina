package dev.lumbina.tv.core.domain.status

import kotlinx.coroutines.flow.Flow

enum class NetworkKind { Wifi, Ethernet, Other, None }

/**
 * The default network: its kind, Wi-Fi signal (0..[MAX_SIGNAL], null if unknown), and whether
 * it reaches the internet.
 */
data class NetworkState(val kind: NetworkKind, val signal: Int? = null, val online: Boolean = false) {
    companion object {
        const val MAX_SIGNAL = 4
        val NONE = NetworkState(NetworkKind.None)
    }
}

/** The device's own settings, where Lumbina sends the user for what it can't set itself. */
interface DeviceScreens {
    /** Opens the network settings, or the nearest screen the firmware has; false when nothing opens. */
    fun openNetwork(): Boolean

    /** Whether the firmware has settings of its own to show. */
    fun hasSettings(): Boolean

    /** Opens the device's settings; false when the firmware has none to show. */
    fun openSettings(): Boolean
}

fun interface ConnectivityStatus {
    fun observe(): Flow<NetworkState>
}

/** A notification another app posted; [app] is the app's name. */
data class Notice(
    val key: String,
    val packageName: String,
    val app: String,
    val title: String,
    val text: String?,
    val postedAt: Long,
    /** False when the notification points to nothing: then it can only be taken off the list. */
    val canOpen: Boolean = true,
)

/**
 * Notifications of other apps, through a notification listener the user (or adb) allowed.
 * Without that permission [hasAccess] stays false and there are no notices.
 */
interface NotificationFeed {
    val hasAccess: Flow<Boolean>

    fun notices(): Flow<List<Notice>>

    /** Opens what the notification points to; false if it has nothing to open. */
    fun open(key: String): Boolean

    fun dismiss(key: String)

    fun dismissAll()
}

/** What another app is playing or has paused (its media session). */
data class NowPlaying(val title: String, val artist: String?, val packageName: String, val playing: Boolean)

/** Media sessions of other apps; needs the same access as [NotificationFeed] (else nothing plays). */
interface MediaSessions {
    fun nowPlaying(): Flow<NowPlaying?>

    fun playPause()

    fun next()

    fun previous()
}
