@file:Suppress("ViewModelForwarding") // the pages call the view model's commands

package dev.lumbina.tv.feature.onboarding

import android.text.format.Formatter
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import dev.lumbina.tv.core.ui.PillShape
import dev.lumbina.tv.core.ui.dialogSurface
import dev.lumbina.tv.core.ui.keyScroll
import java.util.Locale
import kotlinx.coroutines.delay

/** A page of the first-run setup. Which ones a device gets depends on what is left to do on it. */
internal enum class WizardStep { Language, Welcome, Service, Channels, Notices, Voice, Done }

/** The pages for [status], in order; [again]: the setup was asked back, so there is nobody to welcome. */
internal fun wizardSteps(status: SetupStatus, languageFirst: Boolean, again: Boolean): List<WizardStep> = buildList {
    if (!again) {
        if (languageFirst) add(WizardStep.Language)
        add(WizardStep.Welcome)
    }
    if (!status.serviceRunning) add(WizardStep.Service)
    if (status.channels != null) add(WizardStep.Channels)
    if (status.noticesMissing) add(WizardStep.Notices)
    if (status.voice?.ready == false) add(WizardStep.Voice)
    add(WizardStep.Done)
}

/**
 * The first-run setup, over the whole screen: one page per thing a fresh Lumbina can't do yet, each
 * with what it is for, its button, and "Later". Nothing is required: "Skip setup" leaves at once,
 * and the steps not taken stay in the settings. It shows by itself once; after that, when asked back.
 */
@Composable
fun SetupWizardRoute(modifier: Modifier = Modifier, viewModel: SetupViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    var steps by rememberSaveable { mutableStateOf<List<WizardStep>?>(null) }
    var at by rememberSaveable { mutableIntStateOf(0) }
    val current = status
    // Until the setup knows what it shows (it reads the device, or waits for the service Lumbina
    // brings back), home is not shown under it: it would flash before the setup's window comes.
    if (pending && steps == null) Cover()
    if (current == null) {
        // Finished or skipped: asked back, it starts over.
        if (steps != null) {
            SideEffect {
                steps = null
                at = 0
            }
        }
        return
    }
    if (steps == null) {
        // While the app is still bringing the service back after a power-on, it is too early to say what is left.
        // Asked back, it opens at once: its first page is the service's, which shows the starting itself.
        if (current.starting && !current.again) return
        if (current.complete && !current.again) {
            // Nothing to set up: Lumbina was updated on a device where it all works already.
            LaunchedEffect(Unit) { viewModel.finish() }
            return
        }
        steps = wizardSteps(current, viewModel.languageFirst, current.again)
    }
    val pages = steps
    // Home is covered under the setup's window too: made anew (a language chosen recreates the
    // screen), that window comes a moment after the screen, and home would flash between.
    if (pages != null) {
        Cover()
        Wizard(pages, at.coerceIn(pages.indices), onAt = { at = it }, current, viewModel, modifier)
    }
}

/** The setup's ground over the whole screen, under its window. */
@Composable
private fun Cover() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

/** The page [at] of [pages], in a window over everything. Back goes a page back, but not to the language. */
@Suppress("LongParameterList")
@Composable
private fun Wizard(
    pages: List<WizardStep>,
    at: Int,
    onAt: (Int) -> Unit,
    current: SetupStatus,
    viewModel: SetupViewModel,
    modifier: Modifier = Modifier,
) {
    val step = pages[at]
    val next = { if (at < pages.lastIndex) onAt(at + 1) else viewModel.finish() }
    // A language other than the screen's was chosen: the screen is about to be made anew in it.
    // Until then the next page is not drawn, or it would show for a moment in the old language.
    var relanguaging by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (at > 0 && pages[at - 1] != WizardStep.Language) onAt(at - 1) },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        if (relanguaging) return@Dialog Cover()
        WizardFrame(
            // The language is asked before the steps begin: it is not one of them.
            position = pages.filter { it != WizardStep.Language }.indexOf(step).takeIf { it >= 0 },
            count = pages.count { it != WizardStep.Language },
            onSkip = viewModel::finish.takeIf { step != WizardStep.Done && step != WizardStep.Language },
            modifier = modifier,
        ) {
            when (step) {
                WizardStep.Language -> LanguagePage(viewModel, next) { relanguaging = true }
                WizardStep.Welcome -> WelcomePage(viewModel, next)
                WizardStep.Service -> ServicePage(current, viewModel, next)
                WizardStep.Channels -> ChannelsPage(current.channels, viewModel::onChannels, next)
                WizardStep.Notices -> NoticesPage(current.noticesMissing, viewModel::openNotificationAccess, next)
                WizardStep.Voice -> VoicePage(current.voice, viewModel::downloadSpeechModels, next)
                WizardStep.Done -> DonePage(current, viewModel::finish)
            }
        }
    }
}

/** The page's frame: the steps as a bar with "Step 2 of 4", "Skip setup" at the right, the page under them. */
@Composable
private fun WizardFrame(
    position: Int?,
    count: Int,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(28.dp),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 72.dp, vertical = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            if (position != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(count) { index ->
                        val color = MaterialTheme.colorScheme.run { if (index <= position) primary else surfaceVariant }
                        Box(Modifier.width(44.dp).height(5.dp).background(color, PillShape))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    stringResource(R.string.wizard_step, position + 1, count),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (onSkip != null) OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.wizard_skip)) }
        }
        // The left of the page is the text; the right is kept for a picture of what the device will show.
        Box(Modifier.fillMaxWidth(PAGE_WIDTH).fillMaxHeight()) { content() }
    }
}

/**
 * One page: its [title] and [text], [facts] in a block of their own (what the step lets Lumbina
 * see and do), a [note] on how it went, and the buttons. The first button takes the focus, and
 * again whenever [stage] changes: a step that moves on swaps its buttons, and the focus left with
 * nothing would go up to "Skip setup". The buttons stay on screen; a text taller than the page
 * (long facts, large text) scrolls with up and down before the focus moves on, and fades out at
 * the bottom while there is more.
 */
@Composable
internal fun WizardPage(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    facts: List<String> = emptyList(),
    note: String? = null,
    problem: Boolean = false,
    stage: Any? = null,
    buttons: @Composable (Modifier) -> Unit,
) {
    val first = remember { FocusRequester() }
    // Also when the window comes back: a step that moved on while the user was in the system's
    // settings (Android 9) has its button focused but drawn as not; focused anew, it is marked.
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val focusManager = LocalFocusManager.current
    LaunchedEffect(stage, windowFocused) {
        if (!windowFocused) return@LaunchedEffect
        focusManager.clearFocus()
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    val scroll = rememberScrollState()
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = modifier.fillMaxHeight().keyScroll(scroll),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .moreBelow(scroll, MaterialTheme.colorScheme.background)
                .verticalScroll(scroll),
        ) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            Text(text, style = MaterialTheme.typography.titleMedium)
            if (facts.isNotEmpty()) {
                val quiet = MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .dialogSurface()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    facts.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = quiet)
                    }
                }
            }
            if (note != null) {
                val color = MaterialTheme.colorScheme.run { if (problem) error else onSurface }
                Text(note, style = MaterialTheme.typography.bodyLarge, color = color)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons(Modifier.focusRequester(first)) }
    }
}

/**
 * While the text goes on below, its bottom edge fades into the page's [background]: a line cut
 * there says that ▼ shows more. Drawn over the text, which takes no layer, so it is cheap anywhere.
 */
private fun Modifier.moreBelow(scroll: ScrollState, background: Color): Modifier = drawWithCache {
    val edge = MORE_EDGE.toPx()
    val fade = Brush.verticalGradient(
        listOf(Color.Transparent, background),
        startY = size.height - edge,
        endY = size.height,
    )
    onDrawWithContent {
        drawContent()
        if (scroll.canScrollForward) drawRect(fade, Offset(0f, size.height - edge), Size(size.width, edge))
    }
}

private val MORE_EDGE = 48.dp

@Composable
private fun DonePage(status: SetupStatus, onFinish: () -> Unit) {
    val left = listOfNotNull(
        stringResource(R.string.setup_home_title).takeIf { !status.serviceRunning },
        stringResource(R.string.wizard_channels_title).takeIf { status.channels != null },
        stringResource(R.string.wizard_notices_title).takeIf { status.noticesMissing },
        stringResource(R.string.setup_voice_title).takeIf { status.voice?.ready == false },
    )
    WizardPage(
        title = stringResource(R.string.wizard_done_title),
        text = stringResource(if (left.isEmpty()) R.string.wizard_done_all else R.string.wizard_done_some),
        note = left.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.wizard_done_left, it.joinToString()) },
    ) { focus ->
        Button(onClick = onFinish, modifier = focus) { Text(stringResource(R.string.wizard_to_home)) }
    }
}

/** How long "Done" stays on a step before the next one. */
internal const val DONE_MILLIS = 1_500L

/**
 * Moves on by itself a moment after [done] becomes true while the page is shown: the user sees
 * that it worked. A step done already when its page opened (come back to with Back) stays.
 */
@Composable
internal fun NextWhen(done: Boolean, next: () -> Unit) {
    val onNext by rememberUpdatedState(next)
    val doneOnEntry = remember { done }
    val go by produceState(false, done) {
        if (done && !doneOnEntry) {
            delay(DONE_MILLIS)
            value = true
        }
    }
    LaunchedEffect(go) { if (go) onNext() }
}

/** The language of [tag], named in the language on screen, with a capital. */
@Composable
internal fun languageName(tag: String): String {
    val shown = LocalConfiguration.current.locales[0]
    return Locale.forLanguageTag(tag).getDisplayLanguage(shown).replaceFirstChar { it.titlecase(shown) }
}

@Composable
internal fun fileSize(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

/** Of the screen's width: the text column of a page. */
private const val PAGE_WIDTH = 0.6f
