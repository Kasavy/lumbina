package dev.lumbina.tv.core.domain.backdrop

/**
 * What the screensaver shows next, from its sources in turn
 * ([dev.lumbina.tv.core.domain.sleep.screensaverSources]). Null when there is nothing to show, e.g.
 * offline with no photos of one's own. Without `videos`, only photos: a video just failed to play.
 */
fun interface BackdropPhotos {
    suspend fun next(width: Int, height: Int, videos: Boolean): BackdropPhoto?
}

/** A photo or a video for the screensaver, with the [credit] to show, if its source gives one. */
sealed interface BackdropPhoto {
    val credit: String?

    /** An encoded image: JPEG, PNG or WebP. */
    class Image(val bytes: ByteArray, override val credit: String? = null) : BackdropPhoto

    /** A video to play through, by its URI: the user's own or a plugin's. */
    data class Video(val uri: String, override val credit: String? = null) : BackdropPhoto
}

/**
 * What to run on a computer (adb) where Lumbina may not make [component] the system screensaver
 * itself: the two settings a [ScreensaverSetup] writes.
 */
fun screensaverCommands(component: String): List<String> = listOf(
    "adb shell settings put secure screensaver_components $component",
    "adb shell settings put secure screensaver_enabled 1",
)

/** Whether Lumbina is the system screensaver, and making it so where the device allows. */
interface ScreensaverSetup {
    /** True when the system starts Lumbina's screensaver after idle time. */
    val isLumbina: Boolean

    /** Makes Lumbina the system screensaver; false without the permission to change it. */
    fun select(): Boolean
}
