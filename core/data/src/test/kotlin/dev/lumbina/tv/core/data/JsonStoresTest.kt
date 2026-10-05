package dev.lumbina.tv.core.data

import androidx.datastore.core.DataStoreFactory
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.weather.Place
import dev.lumbina.tv.core.domain.weather.WeatherSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
