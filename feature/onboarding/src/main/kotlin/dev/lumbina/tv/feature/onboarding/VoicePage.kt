package dev.lumbina.tv.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.speech.ModelState

/** Speech models for the remote's microphone: what is to download, and how each language is doing. */
@Composable
internal fun VoicePage(voice: VoiceSetup?, onDownload: () -> Unit, next: () -> Unit) {
    NextWhen(voice == null || voice.ready, next)
    val models = voice?.models.orEmpty()
    val failed = models.values.any { it is ModelState.Failed }
    val busy = voice?.busy == true
    val text = voiceText(voice)
    val lines = buildList {
        if (busy || failed) models.forEach { (language, state) -> add(modelLine(language, state)) }
        if (busy) add(stringResource(R.string.wizard_voice_goes_on))
    }
    WizardPage(
        title = stringResource(R.string.setup_voice_title),
        text = text,
        note = lines.joinToString("\n").takeIf { it.isNotEmpty() },
        problem = failed,
        stage = Triple(voice?.ready, busy, failed),
    ) { focus ->
        if (voice == null || voice.ready) return@WizardPage DoneButton(next, focus)
        when {
            // The download goes on by itself: going on is the user's. The focus stays where OK was
            // pressed, on what now says how far it is, so a second OK does not leave the page unseen.
            busy -> {
                Button(onClick = {}, modifier = focus) { Text(progressLabel(models.values)) }
                OutlinedButton(onClick = next) { Text(stringResource(R.string.wizard_next)) }
            }

            models.isEmpty() -> Button(onClick = next, modifier = focus) { Text(stringResource(R.string.wizard_next)) }

            else -> {
                val label = if (failed) R.string.setup_voice_retry else R.string.setup_voice_download
                Button(onClick = onDownload, modifier = focus) { Text(stringResource(label)) }
                OutlinedButton(onClick = next) { Text(stringResource(R.string.wizard_later)) }
            }
        }
    }
}

@Composable
private fun voiceText(voice: VoiceSetup?): String {
    val models = voice?.models.orEmpty()
    return when {
        voice == null || voice.ready -> stringResource(R.string.wizard_voice_done)

        models.isEmpty() -> stringResource(R.string.setup_voice_none)

        else -> {
            val names = models.keys.map { languageName(it) }.joinToString()
            stringResource(R.string.setup_voice, names, fileSize(voice.missingBytes))
        }
    }
}

/** How far the download is, all models together: "Downloading, 42%", then "Unpacking…". */
@Composable
private fun progressLabel(states: Collection<ModelState>): String {
    val downloading = states.filterIsInstance<ModelState.Downloading>()
    if (downloading.isEmpty()) return stringResource(R.string.wizard_voice_unpacking)
    val total = downloading.sumOf { it.totalBytes }
    val percent = if (total > 0) (downloading.sumOf { it.bytes } * PERCENT / total).toInt() else 0
    return stringResource(R.string.wizard_voice_downloading, percent)
}

@Composable
private fun modelLine(language: String, state: ModelState): String {
    val name = languageName(language)
    return when (state) {
        ModelState.Missing -> stringResource(R.string.model_missing, name)

        is ModelState.Downloading -> {
            val percent = (state.fraction * PERCENT).toInt()
            stringResource(R.string.model_downloading, name, percent)
        }

        ModelState.Unpacking -> stringResource(R.string.model_unpacking, name)

        ModelState.Installed -> stringResource(R.string.model_installed, name)

        is ModelState.Failed -> stringResource(
            when (state.reason) {
                ModelState.Reason.Network -> R.string.model_failed_network
                ModelState.Reason.Corrupt -> R.string.model_failed_corrupt
                ModelState.Reason.NoSpace -> R.string.model_failed_space
                ModelState.Reason.Storage -> R.string.model_failed_storage
            },
            name,
        )
    }
}

private const val PERCENT = 100
