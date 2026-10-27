package dev.lumbina.tv.core.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettings
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.channels.ChannelSettings
import dev.lumbina.tv.core.domain.sleep.ScreensaverPhotos
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.WeatherSettings
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A DataStore serializer for a @Serializable class, JSON on disk, tolerant of unknown fields. */
internal class JsonSerializer<T>(private val serializer: KSerializer<T>, override val defaultValue: T) : Serializer<T> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: IllegalArgumentException) { // includes SerializationException
        throw CorruptionException("unreadable ${serializer.descriptor.serialName}", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) =
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
}

@Serializable
internal data class StoredPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String? = null,
)

@Serializable
internal data class StoredWeatherSettings(
    val place: StoredPlace? = null,
    val show: Boolean = true,
    val fahrenheit: Boolean = false,
    val found: StoredPlace? = null,
)

internal fun StoredWeatherSettings.toDomain() = WeatherSettings(place?.toDomain(), show, fahrenheit, found?.toDomain())

internal fun WeatherSettings.toStored() = StoredWeatherSettings(place?.toStored(), show, fahrenheit, found?.toStored())

private fun StoredPlace.toDomain() = Place(name, latitude, longitude, region)

private fun Place.toStored() = StoredPlace(name, latitude, longitude, region)

/**
 * On disk: -1 minutes means switched off; null stays "never chosen", i.e. the default.
 * [screensaverPhotos] names a [ScreensaverPhotos] (a plugin with [screensaverPlugin]); before it,
 * [onlinePhotos] off meant the user's own photos, never online ones. The landscapes that came with
 * Lumbina ("Bundled") are gone: that choice follows the wallpaper now.
 */
@Serializable
internal data class StoredSleepSettings(
    val autoSleepMinutes: Int? = null,
    val timerEndsAt: Long? = null,
    val screensaverMinutes: Int? = null,
    val onlinePhotos: Boolean = true,
    val screensaverPhotos: String? = null,
    val screensaverPlugin: String? = null,
    val screensaverChangeMinutes: Int = SleepSettings.DEFAULT_SCREENSAVER_CHANGE_MINUTES,
)

// The screensaver's choices on disk; at file level, not in the @Serializable class's companion.
private const val SCREENSAVER_AS_WALLPAPER = "AsWallpaper"
private const val SCREENSAVER_MY_MEDIA = "MyMedia"

/** Photos of the day: the name from when the choice was "from the internet". */
private const val SCREENSAVER_PHOTO_OF_THE_DAY = "Online"
private const val SCREENSAVER_PLUGIN = "Plugin"

private const val OFF = -1

internal fun StoredSleepSettings.toDomain() = SleepSettings(
    autoSleepMinutes = minutes(autoSleepMinutes, SleepSettings.DEFAULT_AUTO_SLEEP_MINUTES),
    timerEndsAt = timerEndsAt,
    screensaverMinutes = minutes(screensaverMinutes, SleepSettings.DEFAULT_SCREENSAVER_MINUTES),
    screensaverPhotos = when (screensaverPhotos) {
        SCREENSAVER_AS_WALLPAPER -> ScreensaverPhotos.AsWallpaper
        SCREENSAVER_MY_MEDIA -> ScreensaverPhotos.MyMedia
        SCREENSAVER_PHOTO_OF_THE_DAY -> ScreensaverPhotos.PhotoOfTheDay
        SCREENSAVER_PLUGIN -> screensaverPlugin?.let(ScreensaverPhotos::Plugin) ?: ScreensaverPhotos.AsWallpaper
        else -> if (onlinePhotos) ScreensaverPhotos.AsWallpaper else ScreensaverPhotos.MyMedia
    },
    screensaverChangeMinutes = screensaverChangeMinutes,
)

internal fun SleepSettings.toStored() = StoredSleepSettings(
    autoSleepMinutes ?: OFF,
    timerEndsAt,
    screensaverMinutes ?: OFF,
    screensaverPhotos = when (screensaverPhotos) {
        ScreensaverPhotos.AsWallpaper -> SCREENSAVER_AS_WALLPAPER
        ScreensaverPhotos.MyMedia -> SCREENSAVER_MY_MEDIA
        ScreensaverPhotos.PhotoOfTheDay -> SCREENSAVER_PHOTO_OF_THE_DAY
        is ScreensaverPhotos.Plugin -> SCREENSAVER_PLUGIN
    },
    screensaverPlugin = (screensaverPhotos as? ScreensaverPhotos.Plugin)?.id,
    screensaverChangeMinutes = screensaverChangeMinutes,
)

/** Stored minutes: null means never chosen (the default), [OFF] means switched off. */
private fun minutes(stored: Int?, default: Int): Int? = when (stored) {
    null -> default
    OFF -> null
    else -> stored
}

@Serializable
internal data class StoredChannelSettings(
    val hiddenChannels: List<String> = emptyList(),
    val hiddenWatchNext: List<String> = emptyList(),
)

internal fun StoredChannelSettings.toDomain() = ChannelSettings(hiddenChannels.toSet(), hiddenWatchNext.toSet())

internal fun ChannelSettings.toStored() = StoredChannelSettings(hiddenChannels.sorted(), hiddenWatchNext.sorted())

/** On disk the source is a name ("gradient", "media", "photoOfTheDay", "plugin" with [pluginId]). */
@Serializable
internal data class StoredWallpaperSettings(
    val source: String = GRADIENT,
    val pluginId: String? = null,
    val changeMinutes: Int = WallpaperSettings.DEFAULT_CHANGE_MINUTES,
    val dimAtNight: Boolean = true,
    val mediaChangedAt: Long = 0,
    val startWith: String? = null,
) {
    companion object {
        const val GRADIENT = "gradient"
        const val MEDIA = "media"
        const val PHOTO_OF_THE_DAY = "photoOfTheDay"
        const val PLUGIN = "plugin"
    }
}

internal fun StoredWallpaperSettings.toDomain() = WallpaperSettings(
    source = when (source) {
        StoredWallpaperSettings.MEDIA -> WallpaperSource.MyMedia
        StoredWallpaperSettings.PHOTO_OF_THE_DAY -> WallpaperSource.PhotoOfTheDay
        StoredWallpaperSettings.PLUGIN -> pluginId?.let(WallpaperSource::Plugin) ?: WallpaperSource.Gradient
        else -> WallpaperSource.Gradient
    },
    changeMinutes = changeMinutes,
    dimAtNight = dimAtNight,
    mediaChangedAt = mediaChangedAt,
    startWith = startWith,
)

internal fun WallpaperSettings.toStored() = StoredWallpaperSettings(
    source = when (source) {
        WallpaperSource.Gradient -> StoredWallpaperSettings.GRADIENT
        WallpaperSource.MyMedia -> StoredWallpaperSettings.MEDIA
        WallpaperSource.PhotoOfTheDay -> StoredWallpaperSettings.PHOTO_OF_THE_DAY
        is WallpaperSource.Plugin -> StoredWallpaperSettings.PLUGIN
    },
    pluginId = (source as? WallpaperSource.Plugin)?.id,
    changeMinutes = changeMinutes,
    dimAtNight = dimAtNight,
    mediaChangedAt = mediaChangedAt,
    startWith = startWith,
)
