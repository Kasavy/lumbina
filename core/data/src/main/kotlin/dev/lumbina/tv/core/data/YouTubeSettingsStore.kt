package dev.lumbina.tv.core.data

import dev.lumbina.tv.core.domain.text.SearchSettings
import dev.lumbina.tv.core.domain.text.SearchTarget
import dev.lumbina.tv.core.domain.text.YouTubeSettings
import kotlinx.serialization.Serializable

@Serializable
internal data class StoredYouTubeSettings(val app: String? = null)

internal fun StoredYouTubeSettings.toDomain() = YouTubeSettings(app)

internal fun YouTubeSettings.toStored() = StoredYouTubeSettings(app)

@Serializable
internal data class StoredSearchSettings(val target: String? = null)

/** A target this version does not know (a file from a newer one) reads as not chosen. */
internal fun StoredSearchSettings.toDomain() = SearchSettings(SearchTarget.entries.firstOrNull { it.name == target })

internal fun SearchSettings.toStored() = StoredSearchSettings(target?.name)
