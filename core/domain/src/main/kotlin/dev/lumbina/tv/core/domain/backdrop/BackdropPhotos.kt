package dev.lumbina.tv.core.domain.backdrop

/**
 * Photos for the screensaver: the user's own first, otherwise whatever the implementation can
 * find (e.g. online). Null when there is nothing to show, e.g. offline with no photos of one's own.
 */
fun interface BackdropPhotos {
    suspend fun next(width: Int, height: Int): BackdropPhoto?
}

/** An encoded image ([bytes], JPEG/PNG) and the [credit] to show with it, if its source gives one. */
class BackdropPhoto(val bytes: ByteArray, val credit: String? = null)

/** Where the user's own pictures and videos live, so they can be added (e.g. from a phone) and counted. */
interface PhotoFolder {
    fun count(): Int

    /**
     * Stores a picture or video of [size] bytes read from [input] (not closed here); false if
     * [name] isn't a picture or video, or it could not be written whole.
     */
    fun add(name: String, input: java.io.InputStream, size: Long): Boolean

    /** True if [uri] points to a file in this folder. */
    fun contains(uri: String): Boolean

    fun clear()
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
