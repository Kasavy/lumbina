package dev.lumbina.tv.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import dev.lumbina.tv.core.domain.speech.ModelState
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import dev.lumbina.tv.core.domain.speech.VoiceLanguage
import dev.lumbina.tv.core.domain.speech.VoiceLanguageMode
import dev.lumbina.tv.core.domain.speech.VoiceLanguagesState
import dev.lumbina.tv.core.ui.Choice
import dev.lumbina.tv.core.ui.ChoiceDialog
import dev.lumbina.tv.core.ui.ConfirmDialog
import java.text.Collator
import java.util.Locale

/**
 * Voice input: who recognizes (the device, or a cloud service with a key set from the phone
 * page), the recognition mode and the languages in use (enabled or downloaded). The rest
 * wait behind "Add a language…", the device's language first; adding one downloads and enables
 * it. Holding OK on a downloaded language offers to delete it.
 */
@Composable
fun VoicePanel(
    state: VoiceLanguagesState?,
    supported: Boolean,
    commands: Boolean,
    onMode: (VoiceLanguageMode) -> Unit,
    onLanguageClick: (VoiceLanguage) -> Unit,
    onRemove: (VoiceLanguage) -> Unit,
    onToggleCommands: () -> Unit,
    recognizer: Recognizer,
    onChooseProvider: (SpeechProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    var removing by remember { mutableStateOf<VoiceLanguage?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    // Rows keep their place while the panel is open, so a row being toggled stays under focus.
    val order = remember(state == null) { state?.let(::initialOrder).orEmpty() }
    val (rows, others) = state?.languages?.sortedBy { order[it.language] ?: Int.MAX_VALUE }.orEmpty()
        .partition { it.inUse }
    SettingsList(modifier) {
        if (!supported) item { Note(stringResource(R.string.voice_unsupported)) }
        if (state == null) return@SettingsList
        item { Header(stringResource(R.string.voice_mode_header)) }
        providerRows(recognizer, onClick = { choosing = true })
        item {
            ChoiceRow(
                title = stringResource(R.string.voice_mode),
                options = state.modes.map { mode ->
                    Choice(modeText(state, mode), chosen = mode == state.mode) { onMode(mode) }
                },
            )
        }
        item { Note(stringResource(R.string.voice_mode_hint)) }
        item {
            SwitchRow(
                title = stringResource(R.string.voice_commands),
                hint = stringResource(R.string.voice_commands_hint),
                checked = commands,
                onClick = onToggleCommands,
            )
        }
        item { Header(stringResource(R.string.voice_languages_header)) }
        item { Note(stringResource(R.string.voice_languages_hint)) }
        items(rows, key = { it.language }) { language ->
            SwitchRow(
                title = nativeName(language.language),
                hint = status(language),
                checked = language.enabled,
                onClick = { onLanguageClick(language) },
                onLongClick = { if (language.model != ModelState.Missing) removing = language },
                holdIcon = R.drawable.ic_delete.takeIf { language.model != ModelState.Missing },
            )
        }
        if (others.isNotEmpty()) item { AddLanguageRow(others.size, onClick = { adding = true }) }
    }
    if (choosing) {
        ProviderDialog(recognizer, onChoose = onChooseProvider, onDismiss = { choosing = false })
    }
    if (adding) {
        AddLanguageDialog(others, onAdd = onLanguageClick, onDismiss = { adding = false })
    }
    removing?.let { language ->
        RemoveLanguageDialog(language, onRemove = onRemove, onDismiss = { removing = null })
    }
}

@Composable
private fun RemoveLanguageDialog(
    language: VoiceLanguage,
    onRemove: (VoiceLanguage) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmDialog(
        title = stringResource(R.string.voice_remove_title, nativeName(language.language)),
        text = stringResource(R.string.voice_remove_text, size(language.sizeBytes)),
        confirmLabel = stringResource(R.string.voice_remove_confirm),
        cancelLabel = stringResource(R.string.action_cancel),
        onConfirm = {
            onRemove(language)
            onDismiss()
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/** Shown on the page: enabled, or with a model downloaded (or on its way). */
private val VoiceLanguage.inUse: Boolean get() = enabled || model != ModelState.Missing

@Composable
private fun AddLanguageRow(more: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SettingRow(
        onClick = onClick,
        headlineContent = { Text(stringResource(R.string.voice_add)) },
        trailingContent = { Text(pluralStringResource(R.plurals.voice_add_more, more, more) + " ›") },
        modifier = modifier,
    )
}

/** The languages not in use: the device's language first, then by their own names. */
@Composable
private fun AddLanguageDialog(
    languages: List<VoiceLanguage>,
    onAdd: (VoiceLanguage) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val device = LocalConfiguration.current.locales[0].language
    val byName = compareBy<VoiceLanguage> { it.language != device }
        .thenBy(Collator.getInstance(Locale.ROOT)) { nativeName(it.language) }
    val sorted = languages.sortedWith(byName)
    val sizes = sorted.map { size(it.sizeBytes) }
    ChoiceDialog(
        title = stringResource(R.string.voice_add_title),
        hint = stringResource(R.string.voice_add_hint),
        choices = sorted.mapIndexed { i, language ->
            val local = localName(language.language)
            val detail = if (local == nativeName(language.language)) sizes[i] else "$local · ${sizes[i]}"
            Choice(nativeName(language.language), detail) { onAdd(language) }
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/** Enabled languages in order of preference, then the others by name in the UI language. */
private fun initialOrder(state: VoiceLanguagesState): Map<String, Int> {
    val (enabled, others) = state.languages.partition { it.enabled }
    return (
        enabled + others.sortedBy {
            localName(it.language)
        }
        ).withIndex().associate { (i, row) -> row.language to i }
}

@Composable
private fun modeText(state: VoiceLanguagesState, mode: VoiceLanguageMode): String = when (mode) {
    VoiceLanguageMode.Auto -> stringResource(
        R.string.voice_mode_auto,
        state.enabled.filter { it.installed }.joinToString { nativeName(it.language) },
    )

    is VoiceLanguageMode.Only -> stringResource(R.string.voice_mode_only, nativeName(mode.language))
}

@Composable
private fun status(language: VoiceLanguage): String {
    val detail = when (val model = language.model) {
        ModelState.Installed -> stringResource(R.string.voice_model_installed, size(language.sizeBytes))

        ModelState.Missing -> stringResource(R.string.voice_model_missing, size(language.sizeBytes))

        is ModelState.Downloading -> stringResource(
            R.string.voice_model_downloading,
            (model.fraction * PERCENT).toInt(),
        )

        ModelState.Unpacking -> stringResource(R.string.voice_model_unpacking)

        is ModelState.Failed -> stringResource(failureText(model.reason))
    }
    val localName = localName(language.language)
    return if (localName == nativeName(language.language)) detail else "$localName · $detail"
}

private fun failureText(reason: ModelState.Reason): Int = when (reason) {
    ModelState.Reason.Network -> R.string.voice_model_failed_network
    ModelState.Reason.Corrupt -> R.string.voice_model_failed_corrupt
    ModelState.Reason.NoSpace -> R.string.voice_model_failed_space
    ModelState.Reason.Storage -> R.string.voice_model_failed_storage
}

@Composable
private fun size(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** The language's name for itself: "Русский", "Deutsch", "中文". */
internal fun nativeName(language: String): String =
    Locale.forLanguageTag(language).let { it.getDisplayLanguage(it).replaceFirstChar { c -> c.titlecase(it) } }

/** The language's name in the UI language, for recognizing unfamiliar scripts. */
private fun localName(language: String): String =
    Locale.forLanguageTag(language).getDisplayLanguage(Locale.getDefault()).replaceFirstChar { it.titlecase() }

private const val PERCENT = 100
