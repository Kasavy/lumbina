package dev.lumbina.tv.speech.cloud

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dev.lumbina.tv.core.domain.speech.CloudSpeechEngine
import dev.lumbina.tv.core.domain.speech.SpeechProvider

/** Keys the cloud recognizers by the provider they are. */
@MapKey
annotation class SpeechProviderKey(val value: SpeechProvider)

@Module
@InstallIn(SingletonComponent::class)
internal interface CloudModule {
    @Binds
    @IntoMap
    @SpeechProviderKey(SpeechProvider.OpenAi)
    fun openAi(impl: OpenAiRecognizer): CloudSpeechEngine

    @Binds
    @IntoMap
    @SpeechProviderKey(SpeechProvider.Yandex)
    fun yandex(impl: YandexRecognizer): CloudSpeechEngine
}
