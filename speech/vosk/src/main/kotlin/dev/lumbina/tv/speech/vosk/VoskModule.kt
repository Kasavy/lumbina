package dev.lumbina.tv.speech.vosk

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.lumbina.tv.core.domain.speech.DefaultVoiceLanguages
import dev.lumbina.tv.core.domain.speech.DeviceSpeech
import dev.lumbina.tv.core.domain.speech.SpeechEngine
import dev.lumbina.tv.core.domain.speech.SpeechModels
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal interface VoskModule {
    /** Recognition itself runs in [VoiceService]'s process, on a [VoskSpeechEngine] of its own. */
    @Binds @DeviceSpeech
    fun speechEngine(impl: RemoteSpeechEngine): SpeechEngine

    @Binds fun speechModels(impl: VoskSpeechModels): SpeechModels

    @Binds fun modelSource(impl: HttpModelSource): ModelSource

    @Binds fun defaultLanguages(impl: VoskDefaultLanguages): DefaultVoiceLanguages

    companion object {
        @Provides
        @Singleton
        fun modelStore(@ApplicationContext context: Context) = VoskModelStore(File(context.filesDir, "models"))
    }
}
