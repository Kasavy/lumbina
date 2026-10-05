package dev.lumbina.tv.core.data

import androidx.datastore.core.DataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreSettingsTest {

    private class FullDisk(private val value: Int) : DataStore<Int> {
        var writes = 0
        override val data: Flow<Int> = flowOf(value)

        override suspend fun updateData(transform: suspend (t: Int) -> Int): Int {
            writes++
            throw IOException("No space left on device")
        }
    }

    @Test
    fun `a failed write keeps the old value instead of throwing`() = runTest {
        val store = FullDisk(1)
        val repository = DataStoreSettings(store, toDomain = { it }, toStored = { it })
        repository.update { it + 1 }
        assertEquals(1, store.writes)
    }
}
