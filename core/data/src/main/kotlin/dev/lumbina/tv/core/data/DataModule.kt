package dev.lumbina.tv.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.data.access.StoredAccessSettings
import dev.lumbina.tv.core.data.access.toDomain
import dev.lumbina.tv.core.data.access.toStored
import dev.lumbina.tv.core.data.home.RoomHomeLayoutRepository
import dev.lumbina.tv.core.data.home.StoredHomeAppearance
import dev.lumbina.tv.core.data.home.toDomain
import dev.lumbina.tv.core.data.home.toStored
import dev.lumbina.tv.core.domain.access.AccessSettings
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.app.LaunchHistory
import dev.lumbina.tv.core.domain.app.RecentAppsRepository
import dev.lumbina.tv.core.domain.backdrop.MediaArrival
import dev.lumbina.tv.core.domain.backdrop.MediaArrivalRepository
import dev.lumbina.tv.core.domain.backdrop.MediaMarks
import dev.lumbina.tv.core.domain.backdrop.MediaMarksRepository
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettings
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.channels.ChannelSettings
import dev.lumbina.tv.core.domain.channels.ChannelSettingsRepository
import dev.lumbina.tv.core.domain.home.HomeAppearance
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.home.HomeLayoutRepository
import dev.lumbina.tv.core.domain.input.InputSettings
import dev.lumbina.tv.core.domain.input.InputSettingsRepository
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettingsRepository
import dev.lumbina.tv.core.domain.remote.RemoteSettings
import dev.lumbina.tv.core.domain.remote.RemoteSettingsRepository
import dev.lumbina.tv.core.domain.settings.SettingsRepository
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.speech.DefaultVoiceLanguages
import dev.lumbina.tv.core.domain.speech.SpeechKeys
import dev.lumbina.tv.core.domain.speech.VoiceSettings
import dev.lumbina.tv.core.domain.speech.VoiceSettingsRepository
import dev.lumbina.tv.core.domain.text.SearchSettings
import dev.lumbina.tv.core.domain.text.SearchSettingsRepository
import dev.lumbina.tv.core.domain.text.YouTubeSettings
import dev.lumbina.tv.core.domain.text.YouTubeSettingsRepository
import dev.lumbina.tv.core.domain.weather.WeatherSettings
import dev.lumbina.tv.core.domain.weather.WeatherSettingsRepository
import java.io.File
import javax.inject.Singleton
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

// One @Provides per store; splitting the module would only scatter the wiring.
@Suppress("TooManyFunctions")
@Module
@InstallIn(SingletonComponent::class)
internal object DataModule {

    /** Multi-process: the launcher UI writes it, the service process running voice input reads it. */
    @Provides
    @Singleton
    fun voiceSettings(@ApplicationContext context: Context, defaults: DefaultVoiceLanguages): VoiceSettingsRepository =
        context.settings(
            "voice_settings",
            StoredVoiceSettings(),
            toDomain = { it.toDomain(defaults) },
            toStored = VoiceSettings::toStored,
            multiProcess = true,
        )

    /** Multi-process: set from the phone page (in the service process), shown in the settings. */
    @Provides
    @Singleton
    fun speechKeys(@ApplicationContext context: Context): SpeechKeys = DataStoreSpeechKeys(
        context.jsonStore("speech_keys", StoredSpeechKeys.serializer(), StoredSpeechKeys(), multiProcess = true),
        KeystoreCipher(),
    )

    /** Multi-process: the UI sets the timer, the service process (which sees the remote) acts on it. */
    @Provides
    @Singleton
    fun sleepSettings(@ApplicationContext context: Context): SleepSettingsRepository = context.settings(
        "sleep_settings",
        StoredSleepSettings(),
        StoredSleepSettings::toDomain,
        SleepSettings::toStored,
        multiProcess = true,
    )

    @Provides
    @Singleton
    fun weatherSettings(@ApplicationContext context: Context): WeatherSettingsRepository = context.settings(
        "weather_settings",
        StoredWeatherSettings(),
        StoredWeatherSettings::toDomain,
        WeatherSettings::toStored,
    )

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): LumbinaDatabase = Room.databaseBuilder(
        context,
        LumbinaDatabase::class.java,
        "lumbina.db",
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        // The remote's service has its own copy in its own process: it hears of changes made in the UI's.
        .enableMultiInstanceInvalidation()
        .build()

    @Provides
    @Singleton
    fun homeLayout(database: LumbinaDatabase): HomeLayoutRepository = RoomHomeLayoutRepository(database.homeLayout())

    /** Multi-process: the phone remote (in the service process) switches to new uploads. */
    @Provides
    @Singleton
    fun wallpaperSettings(@ApplicationContext context: Context): WallpaperSettingsRepository = context.settings(
        "wallpaper_settings",
        StoredWallpaperSettings(),
        StoredWallpaperSettings::toDomain,
        WallpaperSettings::toStored,
        multiProcess = true,
    )

    /** Multi-process: the screensaver may run apart from the settings that set the marks. */
    @Provides
    @Singleton
    fun mediaMarks(@ApplicationContext context: Context): MediaMarksRepository = context.settings(
        "media_marks",
        StoredMediaMarks(),
        StoredMediaMarks::toDomain,
        MediaMarks::toStored,
        multiProcess = true,
    )

    /** Multi-process: written as files come through the phone page's server, in the service process; home says so. */
    @Provides
    @Singleton
    fun mediaArrival(@ApplicationContext context: Context): MediaArrivalRepository = context.settings(
        "media_arrival",
        StoredMediaArrival(),
        StoredMediaArrival::toDomain,
        MediaArrival::toStored,
        multiProcess = true,
    )

    /** Multi-process: the phone page's server runs in the service process; rewritten on first read to make its key. */
    @Provides
    @Singleton
    fun phoneRemoteSettings(@ApplicationContext context: Context): PhoneRemoteSettingsRepository = context.settings(
        "phone_remote",
        StoredPhoneRemoteSettings(),
        StoredPhoneRemoteSettings::toDomain,
        PhoneRemoteSettings::toStored,
        multiProcess = true,
        rewriteOnRead = true,
    )

    /** Multi-process: the switcher (in the service process) keeps it; the home screen may show it. */
    @Provides
    @Singleton
    fun recentApps(@ApplicationContext context: Context): RecentAppsRepository = DataStoreRecentApps(
        context.jsonStore("recent_apps", StoredRecentApps.serializer(), StoredRecentApps(), multiProcess = true),
    )

    /** Multi-process: voice search and links from the phone open in the service process. */
    @Provides
    @Singleton
    fun youTubeSettings(@ApplicationContext context: Context): YouTubeSettingsRepository = context.settings(
        "youtube_settings",
        StoredYouTubeSettings(),
        StoredYouTubeSettings::toDomain,
        YouTubeSettings::toStored,
        multiProcess = true,
    )

    /** Multi-process: a phrase said on a launcher is searched for in the service process. */
    @Provides
    @Singleton
    fun searchSettings(@ApplicationContext context: Context): SearchSettingsRepository = context.settings(
        "search_settings",
        StoredSearchSettings(),
        StoredSearchSettings::toDomain,
        SearchSettings::toStored,
        multiProcess = true,
    )

    /** Multi-process: the switcher (in the service process) reads whether it may close apps. */
    @Provides
    @Singleton
    fun accessSettings(@ApplicationContext context: Context): AccessSettingsRepository = context.settings(
        "access_settings",
        StoredAccessSettings(),
        StoredAccessSettings::toDomain,
        AccessSettings::toStored,
        multiProcess = true,
    )

    @Provides
    @Singleton
    fun channelSettings(@ApplicationContext context: Context): ChannelSettingsRepository = context.settings(
        "channel_settings",
        StoredChannelSettings(),
        StoredChannelSettings::toDomain,
        ChannelSettings::toStored,
    )

    /** Multi-process: set in the settings, acted on by the service process, which sees the remote. */
    @Provides
    @Singleton
    fun remoteSettings(@ApplicationContext context: Context): RemoteSettingsRepository = context.settings(
        "remote_settings",
        StoredRemoteSettings(),
        StoredRemoteSettings::toDomain,
        RemoteSettings::toStored,
        multiProcess = true,
    )

    @Provides
    @Singleton
    fun inputSettings(@ApplicationContext context: Context): InputSettingsRepository = context.settings(
        "input_settings",
        StoredInputSettings(),
        StoredInputSettings::toDomain,
        InputSettings::toStored,
    )

    /** Multi-process: apps are opened from the UI and from the service process (voice, the switcher). */
    @Provides
    @Singleton
    fun launchHistory(@ApplicationContext context: Context): LaunchHistory = DataStoreLaunchHistory(
        context.jsonStore(
            "launch_history",
            StoredLaunchHistory.serializer(),
            StoredLaunchHistory(),
            multiProcess = true,
        ),
    )

    /** Multi-process: the service's overlays (quick menu, recent apps, voice) take its theme and accent. */
    @Provides
    @Singleton
    fun homeAppearance(@ApplicationContext context: Context): HomeAppearanceRepository = context.settings(
        "home_appearance",
        StoredHomeAppearance(),
        StoredHomeAppearance::toDomain,
        HomeAppearance::toStored,
        multiProcess = true,
    )

    /** Settings in the JSON store [name], mapped between their on-disk form [S] and [D]. */
    private inline fun <reified S, D> Context.settings(
        name: String,
        default: S,
        noinline toDomain: (S) -> D,
        noinline toStored: (D) -> S,
        multiProcess: Boolean = false,
        rewriteOnRead: Boolean = false,
    ): SettingsRepository<D> =
        DataStoreSettings(jsonStore(name, serializer<S>(), default, multiProcess), toDomain, toStored, rewriteOnRead)

    /**
     * A JSON DataStore at `datastore/<name>.json` that starts from [default] if its file is unreadable
     * (e.g. cut short by a power loss): losing a setting is better than a launcher that crashes on every start.
     */
    private fun <T> Context.jsonStore(
        name: String,
        serializer: KSerializer<T>,
        default: T,
        multiProcess: Boolean = false,
    ): DataStore<T> {
        val json = JsonSerializer(serializer, default)
        val onCorruption = ReplaceFileCorruptionHandler { default }
        val file = { File(filesDir, "datastore/$name.json") }
        return if (multiProcess) {
            MultiProcessDataStoreFactory.create(json, onCorruption, produceFile = file)
        } else {
            DataStoreFactory.create(json, onCorruption, produceFile = file)
        }
    }
}
