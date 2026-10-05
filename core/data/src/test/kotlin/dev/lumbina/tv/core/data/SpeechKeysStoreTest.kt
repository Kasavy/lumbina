package dev.lumbina.tv.core.data

import androidx.datastore.core.DataStore
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpeechKeysStoreTest {

    /** Stands in for the Keystore: reverses, and can't open what it didn't seal. */
    private val cipher = object : SecretCipher {
        override fun seal(plain: String) = "sealed:" + plain.reversed()

        override fun open(sealed: String) = sealed.removePrefix("sealed:").takeIf { it != sealed }?.reversed()
    }

    private class MemoryStore(initial: StoredSpeechKeys = StoredSpeechKeys()) : DataStore<StoredSpeechKeys> {
        val state = MutableStateFlow(initial)
        var full = false
        override val data: Flow<StoredSpeechKeys> = state

        override suspend fun updateData(
            transform: suspend (t: StoredSpeechKeys) -> StoredSpeechKeys,
        ): StoredSpeechKeys {
            if (full) throw IOException("no space left")
            return transform(state.value).also { state.value = it }
        }
    }

    @Test
    fun `keys are stored sealed, trimmed, and a blank one removes the key`() = runTest {
        val store = MemoryStore()
        val keys = DataStoreSpeechKeys(store, cipher)

        keys.set(SpeechProvider.OpenAi, " sk-secret\n")
        keys.set(SpeechProvider.Yandex, "AQVN-key")
        val both = mapOf(SpeechProvider.OpenAi to "sk-secret", SpeechProvider.Yandex to "AQVN-key")
        assertEquals(both, keys.keys.first())
        assertFalse(store.state.value.sealed.values.any { "sk-secret" in it })
        assertEquals(setOf(SpeechProvider.OpenAi, SpeechProvider.Yandex), keys.providers.first())

        keys.set(SpeechProvider.Yandex, " ")
        assertEquals(mapOf(SpeechProvider.OpenAi to "sk-secret"), keys.keys.first())
    }

    @Test
    fun `a key that couldn't be stored says so`() = runTest {
        val store = MemoryStore().apply { full = true }
        assertFalse(DataStoreSpeechKeys(store, cipher).set(SpeechProvider.OpenAi, "sk-secret"))
    }

    @Test
    fun `a key that can't be opened is left out`() = runTest {
        val store = MemoryStore(StoredSpeechKeys(mapOf("OpenAi" to "garbage", "Gone" to "sealed:x")))
        assertEquals(emptyMap<SpeechProvider, String>(), DataStoreSpeechKeys(store, cipher).keys.first())
    }
}
