package dev.lumbina.tv.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import dev.lumbina.tv.core.domain.speech.SpeechKeys
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

/** On-disk form: each provider's key sealed by a [SecretCipher], by provider name. */
@Serializable
internal data class StoredSpeechKeys(val sealed: Map<String, String> = emptyMap())

/** Seals secrets for storage; [open] gives null for what it can't open. */
internal interface SecretCipher {
    fun seal(plain: String): String

    fun open(sealed: String): String?
}

internal class DataStoreSpeechKeys(private val store: DataStore<StoredSpeechKeys>, private val cipher: SecretCipher) :
    SpeechKeys {

    override val keys: Flow<Map<SpeechProvider, String>> = store.data.map { stored ->
        stored.sealed.mapNotNull { (name, sealed) ->
            val provider = providerNamed(name) ?: return@mapNotNull null
            cipher.open(sealed)?.let { provider to it }
        }.toMap()
    }

    override val providers: Flow<Set<SpeechProvider>> =
        store.data.map { stored -> stored.sealed.keys.mapNotNull(::providerNamed).toSet() }

    override suspend fun set(provider: SpeechProvider, key: String): Boolean = try {
        store.updateData { stored ->
            val sealed = if (key.isBlank()) {
                stored.sealed - provider.name
            } else {
                stored.sealed + (provider.name to cipher.seal(key.trim()))
            }
            stored.copy(sealed = sealed)
        }
        true
    } catch (_: IOException) {
        false
    } catch (_: GeneralSecurityException) {
        false
    } catch (_: ProviderException) {
        // The Keystore failing underneath.
        false
    }

    private fun providerNamed(name: String) = SpeechProvider.entries.firstOrNull { it.name == name }
}

/**
 * AES-GCM under a key the Android Keystore holds and never hands out, so the file alone (a copy
 * off a rooted device) doesn't give the API keys away. Opening never makes a key: sealed data
 * implies one exists.
 */
internal class KeystoreCipher : SecretCipher {
    private val keyStore by lazy { KeyStore.getInstance(KEYSTORE).apply { load(null) } }

    override fun seal(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key() ?: newKey()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    override fun open(sealed: String): String? = try {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key() ?: return null, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString()
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ProviderException) {
        // The Keystore's own failures (a lost or broken key on some Android 9 boxes): unreadable.
        null
    }

    private fun key(): SecretKey? = keyStore.getKey(ALIAS, null) as? SecretKey

    private fun newKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
        init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
    }.generateKey()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "speech_keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
