package dev.lumbina.tv.core.data

import androidx.datastore.core.DataStoreFactory
import dev.lumbina.tv.core.domain.channels.ChannelSettings
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import dev.lumbina.tv.core.domain.sleep.ScreensaverPhotos
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.WeatherSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JsonStoresTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `sleep settings default to three hours and remember off`() = runTest {
        val store = DataStoreFactory.create(
            JsonSerializer(StoredSleepSettings.serializer(), StoredSleepSettings()),
            scope = backgroundScope,
            produceFile = { File(temp.root, "sleep.json") },
        )
        val repository = DataStoreSettings(store, StoredSleepSettings::toDomain, SleepSettings::toStored)
        assertEquals(SleepSettings(autoSleepMinutes = 180), repository.settings.first())
        repository.update { it.copy(autoSleepMinutes = null, timerEndsAt = 42) }
        assertEquals(SleepSettings(autoSleepMinutes = null, timerEndsAt = 42), repository.settings.first())
    }

    @Test
    fun `the screensaver's plugin and change interval round-trip, and the old online choice is photos of the day`() {
        val plugin = SleepSettings(screensaverPhotos = ScreensaverPhotos.Plugin("p/s"), screensaverChangeMinutes = 5)
        assertEquals(plugin, plugin.toStored().toDomain())
        val old = StoredSleepSettings(screensaverPhotos = "Online").toDomain()
        assertEquals(ScreensaverPhotos.PhotoOfTheDay, old.screensaverPhotos)
        assertEquals(1, old.screensaverChangeMinutes)
    }

    @Test
    fun `channel settings round-trip, and an old file without the watch-next apps reads`() {
        val settings = ChannelSettings(hiddenChannels = setOf("a/x"), hiddenWatchNext = setOf("kino"))
        assertEquals(settings, settings.toStored().toDomain())
        val old = Json.decodeFromString<StoredChannelSettings>("""{"hiddenChannels":["a/x"]}""")
        assertEquals(ChannelSettings(hiddenChannels = setOf("a/x")), old.toDomain())
    }

    @Test
    fun `the gone landscapes, a plugin without its id and the old own-photos switch read sensibly`() {
        assertEquals(
            ScreensaverPhotos.AsWallpaper,
            StoredSleepSettings(screensaverPhotos = "Bundled").toDomain().screensaverPhotos,
        )
        assertEquals(
            ScreensaverPhotos.AsWallpaper,
            StoredSleepSettings(screensaverPhotos = "Plugin").toDomain().screensaverPhotos,
        )
        assertEquals(ScreensaverPhotos.MyMedia, StoredSleepSettings(onlinePhotos = false).toDomain().screensaverPhotos)
    }

    @Test
    fun `weather place round-trips, null means by IP`() = runTest {
        val store = DataStoreFactory.create(
            JsonSerializer(StoredWeatherSettings.serializer(), StoredWeatherSettings()),
            scope = backgroundScope,
            produceFile = { File(temp.root, "weather.json") },
        )
        val repository = DataStoreSettings(store, StoredWeatherSettings::toDomain, WeatherSettings::toStored)
        assertEquals(WeatherSettings(), repository.settings.first())
        val kazan = Place("Казань", 55.79, 49.12, "Татарстан")
        repository.update { it.copy(place = kazan) }
        assertEquals(WeatherSettings(kazan), repository.settings.first())
    }

    @Test
    fun `the phone remote gets a key on first read and keeps it`() = runTest {
        val store = DataStoreFactory.create(
            JsonSerializer(StoredPhoneRemoteSettings.serializer(), StoredPhoneRemoteSettings()),
            scope = backgroundScope,
            produceFile = { File(temp.root, "phone.json") },
        )
        val repository = DataStoreSettings(
            store,
            StoredPhoneRemoteSettings::toDomain,
            PhoneRemoteSettings::toStored,
            rewriteOnRead = true,
        )
        val key = repository.settings.first().key
        assertTrue(key.isNotEmpty())
        assertEquals(key, repository.settings.first().key)
    }

    @Test
    fun `launch history counts the last two weeks`() = runTest {
        // One write only: on Windows a DataStore file can't be replaced once it exists.
        val store = DataStoreFactory.create(
            JsonSerializer(StoredLaunchHistory.serializer(), StoredLaunchHistory()),
            scope = backgroundScope,
            produceFile = { File(temp.root, "launches.json") },
        )
        var now = 100 * DAY
        val history = DataStoreLaunchHistory(store) { now }
        history.add("a")
        assertEquals(mapOf("a" to 1L), history.openings().first())
        now += 15 * DAY
        assertEquals(emptyMap<String, Long>(), history.openings().first())
    }

    private companion object {
        const val DAY = 24 * 60 * 60_000L
    }
}
