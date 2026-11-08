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
