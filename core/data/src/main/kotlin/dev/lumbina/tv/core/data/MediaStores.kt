package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.domain.backdrop.MediaArrival
import dev.lumbina.tv.core.domain.backdrop.MediaMarks
import kotlinx.serialization.Serializable

/** On disk: the ids, sorted, so the file reads the same after the same marks. */
@Serializable
internal data class StoredMediaMarks(
    val offWallpaper: List<String> = emptyList(),
    val offScreensaver: List<String> = emptyList(),
    val unplayable: List<String> = emptyList(),
)

internal fun StoredMediaMarks.toDomain() = MediaMarks(offWallpaper.toSet(), offScreensaver.toSet(), unplayable.toSet())

internal fun MediaMarks.toStored() =
    StoredMediaMarks(offWallpaper.sorted(), offScreensaver.sorted(), unplayable.sorted())

@Serializable
internal data class StoredMediaArrival(val at: Long = 0, val photos: Int = 0, val videos: Int = 0, val banners: Int = 0)

internal fun StoredMediaArrival.toDomain() = MediaArrival(at, photos, videos, banners)

internal fun MediaArrival.toStored() = StoredMediaArrival(at, photos, videos, banners)
