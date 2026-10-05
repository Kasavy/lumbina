package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog

/** Who recognizes; for a cloud service, what that means for the user. */
internal fun LazyListScope.providerRows(recognizer: Recognizer, onClick: () -> Unit) {
    item {
        SettingRow(
            onClick = onClick,
            headlineContent = { Text(stringResource(R.string.voice_provider)) },
            supportingContent = { Text(providerText(recognizer)) },
        )
    }
    if (recognizer.provider != SpeechProvider.Device) {
        item { Note(stringResource(R.string.voice_provider_cloud_hint, providerName(recognizer.provider))) }
    }
}

@Composable
internal fun ProviderDialog(
    recognizer: Recognizer,
    onChoose: (SpeechProvider) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val noKey = stringResource(R.string.voice_provider_no_key)
    val choices = SpeechProvider.entries.map { provider ->
        val subtitle = when {
            provider == SpeechProvider.Device -> stringResource(R.string.voice_provider_device_hint)
            provider in recognizer.withKey -> stringResource(R.string.voice_provider_key_set)
            else -> noKey
        }
        Choice(providerName(provider), subtitle) { onChoose(provider) }
    }
    ChoiceDialog(
        title = stringResource(R.string.voice_provider),
        choices = choices,
        onDismiss = onDismiss,
        hint = stringResource(R.string.voice_provider_keys_hint),
        modifier = modifier,
    )
}

@Composable
private fun providerName(provider: SpeechProvider): String = stringResource(
    when (provider) {
        SpeechProvider.Device -> R.string.voice_provider_device
        SpeechProvider.OpenAi -> R.string.voice_provider_openai
        SpeechProvider.Yandex -> R.string.voice_provider_yandex
    },
)

@Composable
private fun providerText(recognizer: Recognizer): String {
    val name = providerName(recognizer.provider)
    return if (recognizer.missingKey) "$name · ${stringResource(R.string.voice_provider_no_key)}" else name
}
