package dev.lumbina.tv.speech.vosk

import dev.lumbina.tv.core.domain.speech.DefaultVoiceLanguages
import java.util.Locale
import javax.inject.Inject

/**
 * Before the user picks languages, one: every loaded model costs memory and time per phrase. The
 * device language when its model exists, otherwise English; an installed model (e.g. pushed over
 * adb) wins over one that would have to be downloaded.
 */
internal class VoskDefaultLanguages(private val store: VoskModelStore, private val deviceLanguage: () -> String) :
    DefaultVoiceLanguages {

    @Inject constructor(store: VoskModelStore) : this(store, { Locale.getDefault().language })

    override fun get(): List<String> {
        val catalog = VoskModelCatalog.models.map { it.language }
        val candidates = listOf(deviceLanguage(), FALLBACK).distinct().filter { it in catalog }
        val pick = candidates.firstOrNull(store::isInstalled)
            ?: catalog.firstOrNull(store::isInstalled)
            ?: candidates.firstOrNull()
        return listOfNotNull(pick)
    }

    private companion object {
        const val FALLBACK = "en"
    }
}
