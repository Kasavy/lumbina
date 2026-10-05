package dev.lumbina.tv.platform.tvprovider

import dev.lumbina.tv.core.domain.channels.ArtShape
import dev.lumbina.tv.core.domain.channels.ChannelFeed
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.channels.TvChannel
import dev.lumbina.tv.core.domain.channels.TvChannels

/** One provider row, read by column name; keeps the parsing free of Android's Cursor (and testable). */
internal interface TvRow {
    fun string(column: String): String?

    fun long(column: String): Long?
}

internal object Columns {
    const val ID = "_id"
    const val PACKAGE = "package_name"
    const val DISPLAY_NAME = "display_name"
    const val APP_LINK_INTENT = "app_link_intent_uri"
    const val BROWSABLE = "browsable"
    const val CHANNEL_ID = "channel_id"
    const val TITLE = "title"
    const val EPISODE_TITLE = "episode_title"
    const val SEASON = "season_display_number"
    const val EPISODE = "episode_display_number"
    const val POSTER = "poster_art_uri"
    const val THUMBNAIL = "thumbnail_uri"
    const val ASPECT = "poster_art_aspect_ratio"
    const val THUMBNAIL_ASPECT = "thumbnail_aspect_ratio"
    const val INTENT = "intent_uri"
    const val WEIGHT = "weight"
    const val POSITION = "last_playback_position_millis"
    const val DURATION = "duration_millis"
    const val ENGAGED_AT = "last_engagement_time_utc_millis"

    val CHANNEL = arrayOf(ID, PACKAGE, DISPLAY_NAME, APP_LINK_INTENT, BROWSABLE)
    val PREVIEW_PROGRAM = arrayOf(
        ID, PACKAGE, CHANNEL_ID, TITLE, EPISODE_TITLE, SEASON, EPISODE, POSTER, THUMBNAIL,
        ASPECT, THUMBNAIL_ASPECT, INTENT, BROWSABLE, WEIGHT,
    )
    val WATCH_NEXT = arrayOf(
        ID, PACKAGE, TITLE, EPISODE_TITLE, SEASON, EPISODE, POSTER, THUMBNAIL,
        ASPECT, THUMBNAIL_ASPECT, INTENT, BROWSABLE, POSITION, DURATION, ENGAGED_AT,
    )
}

/** A browsable channel, or null for one hidden by its app or missing required fields. */
internal fun channelOf(row: TvRow, logoUri: (Long) -> String): TvChannel? {
    if (row.long(Columns.BROWSABLE) == 0L) return null
    val id = row.long(Columns.ID) ?: return null
    val name = row.string(Columns.DISPLAY_NAME)?.takeIf { it.isNotBlank() } ?: return null
    return TvChannel(
        id = id,
        packageName = row.string(Columns.PACKAGE) ?: return null,
        name = name,
        appLinkUri = row.string(Columns.APP_LINK_INTENT),
        logoUri = logoUri(id),
    )
}

/** A preview program as stored: the channel it names and its weight (higher first). */
internal class ChannelProgram(val channelId: Long, val card: ProgramCard, val weight: Long)

/**
 * The channels that have programs, each with its programs by weight, one feed per
 * [TvChannel.key] (an app's channels of one name merge, as [TvChannels] promises). A program
 * counts only in a channel of its own app: `channel_id` is whatever the publishing app wrote,
 * and ids are easy to guess, so an app could otherwise put its cards in another app's row.
 */
internal fun feedsOf(channels: List<TvChannel>, programs: List<ChannelProgram>): List<ChannelFeed> {
    val byChannel = programs.groupBy { it.channelId }
    return channels.groupBy { it.key }.values.mapNotNull { same ->
        val cards = same.flatMap { channel ->
            byChannel[channel.id].orEmpty().filter { it.card.packageName == channel.packageName }
        }.sortedByDescending { it.weight }.map { it.card }
        cards.takeIf { it.isNotEmpty() }?.let { ChannelFeed(same.first(), it) }
    }
}

/** A preview or watch-next program, or null when it has no title or was hidden by its app. */
internal fun programOf(row: TvRow): ProgramCard? {
    if (row.long(Columns.BROWSABLE) == 0L) return null
    val title = row.string(Columns.TITLE)?.takeIf { it.isNotBlank() }
        ?: row.string(Columns.EPISODE_TITLE)?.takeIf { it.isNotBlank() }
        ?: return null
    val progress = progressOf(row.long(Columns.POSITION), row.long(Columns.DURATION))
    val poster = row.string(Columns.POSTER)?.takeIf { it.isNotBlank() }
    val aspect = if (poster != null) row.long(Columns.ASPECT) else row.long(Columns.THUMBNAIL_ASPECT)
    return ProgramCard(
        id = row.long(Columns.ID) ?: return null,
        packageName = row.string(Columns.PACKAGE) ?: return null,
        title = title,
        subtitle = subtitleOf(row, title),
        artUri = poster ?: row.string(Columns.THUMBNAIL)?.takeIf { it.isNotBlank() },
        shape = artShapeOf(aspect?.toInt()),
        intentUri = row.string(Columns.INTENT),
        progress = progress,
    )
}

/** Maps TvContract's `ASPECT_RATIO_*` codes to a shape; null without one (never guessed from the program). */
@Suppress("MagicNumber") // TvContract.PreviewPrograms.ASPECT_RATIO_* values
internal fun artShapeOf(aspectRatio: Int?): ArtShape? = when (aspectRatio) {
    0 -> ArtShape.Wide
    1 -> ArtShape.Landscape
    2 -> ArtShape.Classic
    3 -> ArtShape.Square
    4 -> ArtShape.Portrait
    5 -> ArtShape.MoviePoster
    else -> null
}

/** "S4 · E18 · Episode title", leaving out what the app did not give or what repeats the title. */
private fun subtitleOf(row: TvRow, title: String): String? {
    val episodeTitle = row.string(Columns.EPISODE_TITLE)?.takeIf { it.isNotBlank() && it != title }
    return listOfNotNull(
        row.string(Columns.SEASON)?.takeIf { it.isNotBlank() }?.let { "S$it" },
        row.string(Columns.EPISODE)?.takeIf { it.isNotBlank() }?.let { "E$it" },
        episodeTitle,
    ).joinToString(" · ").ifEmpty { null }
}

private fun progressOf(position: Long?, duration: Long?): Float? {
    if (position == null || duration == null) return null
    return if (position > 0 && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null
}
