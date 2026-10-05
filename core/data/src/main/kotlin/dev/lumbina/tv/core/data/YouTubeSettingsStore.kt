package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.domain.text.YouTubeSettings
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredYouTubeSettings(val app: String? = null)

internal fun StoredYouTubeSettings.toDomain() = YouTubeSettings(app)

internal fun YouTubeSettings.toStored() = StoredYouTubeSettings(app)
