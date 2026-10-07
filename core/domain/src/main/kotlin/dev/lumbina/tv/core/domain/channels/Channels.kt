package dev.lumbina.tv.core.domain.channels

import dev.lumbina.tv.core.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow

/** Shape of a program's artwork, from the TV Provider's `poster_art_aspect_ratio`. */
@Suppress("MagicNumber") // the ratios are the point
enum class ArtShape(val widthToHeight: Float) {
    Wide(16f / 9f),
    Landscape(3f / 2f),
    Classic(4f / 3f),
    Square(1f),
    Portrait(2f / 3f),
    MoviePoster(1000f / 1441f),
}

/**
 * A card an app published through the TV Provider: a recommendation in one of its channels or,
 * with [progress], something to continue watching. [intentUri] opens it in the app. [shape] is
 * null when the app did not say; the artwork itself decides then.
 */
data class ProgramCard(
    val id: Long,
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val artUri: String?,
    val shape: ArtShape?,
    val intentUri: String?,
    /** 0..1 for "continue watching"; null when the app gave no playback position. */
    val progress: Float? = null,
)

/** A channel (row of recommendations) an app published; [key] stays the same when the app republishes it. */
data class TvChannel(
    val id: Long,
    val packageName: String,
    val name: String,
    val appLinkUri: String?,
    val logoUri: String?,
) {
    val key: String get() = "$packageName/$name"
}

data class ChannelFeed(val channel: TvChannel, val programs: List<ProgramCard>)

/** The TV Provider as the home screen sees it (the device's own, or Lumbina's companion APK). */
interface TvChannels {
    /** The package of the device's TV Provider; null when it has none, so apps have nowhere to publish channels. */
    fun provider(): String?

    fun isAvailable(): Boolean = provider() != null

    /**
     * Channels with their programs, updated whenever an app publishes; empty without a provider.
     * One feed per [TvChannel.key]: an app's channels of one name come as one, since lists key
     * their rows by it and the user hides them together anyway.
     */
    fun channels(): Flow<List<ChannelFeed>>

    /** "Continue watching" across apps, most recently watched first. */
    fun watchNext(): Flow<List<ProgramCard>>
}

/**
 * The permission the system's TV Provider asks for before it shows other apps' rows. The user
 * grants it; the provider sends no notice when they do.
 */
interface TvListingsPermission {
    /** The permission to ask the user for, or null when none is needed or it is granted already. */
    fun missing(): String?

    /** It was granted: [TvChannels] reads its rows again. */
    fun onGranted()
}

/** Opens a program in its app; false when neither the program nor its app can be started. */
fun interface ProgramLauncher {
    fun open(card: ProgramCard): Boolean
}

/** Which channel rows the user wants on the home screen. */
data class ChannelSettings(val hiddenChannels: Set<String> = emptySet())

typealias ChannelSettingsRepository = SettingsRepository<ChannelSettings>
