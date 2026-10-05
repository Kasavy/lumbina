package dev.lumbina.tv.feature.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.ui.opensOnRest
import dev.lumbina.tv.core.ui.rememberOpenOnRest
import javax.inject.Inject

/** A page of settings; sections with more than one show them as tabs. */
enum class SettingsPage(val title: Int) {
    Rows(R.string.page_rows),
    Channels(R.string.settings_channels),
    Widgets(R.string.page_widgets),
    Wallpaper(R.string.settings_wallpaper),
    Look(R.string.page_look),
    Voice(R.string.settings_voice),
    Keyboard(R.string.page_keyboard),
    Phone(R.string.settings_phone),
    Sleep(R.string.settings_sleep),
    About(R.string.page_about),
    Access(R.string.page_access),
    State(R.string.page_state),
}

/** The sections in the left column, each with its icon and pages. */
enum class SettingsSection(val title: Int, val icon: Int, val pages: List<SettingsPage>) {
    Home(
        R.string.settings_home,
        R.drawable.ic_section_home,
        listOf(SettingsPage.Rows, SettingsPage.Channels, SettingsPage.Widgets),
    ),
    Look(R.string.section_look, R.drawable.ic_section_look, listOf(SettingsPage.Wallpaper, SettingsPage.Look)),
    Input(
        R.string.section_input,
        R.drawable.ic_section_input,
        listOf(SettingsPage.Voice, SettingsPage.Keyboard, SettingsPage.Phone),
    ),
    Sleep(R.string.settings_sleep, R.drawable.ic_section_sleep, listOf(SettingsPage.Sleep)),
    System(
        R.string.section_system,
        R.drawable.ic_section_system,
        listOf(SettingsPage.About, SettingsPage.Access, SettingsPage.State),
    ),
}

/** Pages this device has no use for: Access needs debugging Lumbina can reach by itself. */
@HiltViewModel
class SettingsPagesViewModel @Inject constructor(profile: DeviceProfile) : ViewModel() {
    val hidden: Set<SettingsPage> = if (profile.localAdb) emptySet() else setOf(SettingsPage.Access)
}

@Composable
fun SettingsRoute(
    onPreviewScreensaver: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsPage? = null,
    pages: SettingsPagesViewModel = hiltViewModel(),
) {
    var section by rememberSaveable {
        mutableStateOf(SettingsSection.entries.firstOrNull { initialPage in it.pages } ?: SettingsSection.Home)
    }
    var page by rememberSaveable { mutableStateOf(initialPage ?: SettingsPage.Rows) }
    // A jump to another page (from Sleep to Access) takes the focused row away: focus goes to the tab.
    var tabsFocusRequests by remember { mutableIntStateOf(0) }
    SettingsScreen(
        section = section,
        page = page,
        hidden = pages.hidden,
        tabsFocusRequests = tabsFocusRequests,
        onSection = {
            section = it
            page = it.pages.first()
        },
        onPage = { page = it },
        modifier = modifier,
    ) {
        val openAccess = if (SettingsPage.Access in pages.hidden) {
            null
        } else {
            {
                section = SettingsSection.System
                page = SettingsPage.Access
                tabsFocusRequests += 1
            }
        }
        SettingsPageContent(page, onPreviewScreensaver, openAccess)
    }
}

/**
 * TV settings layout: the sections on the left (one opens once the focus rests on it), the section's pages
 * as tabs on top when it has several, the page below.
 */
@Composable
fun SettingsScreen(
    section: SettingsSection,
    page: SettingsPage,
    onSection: (SettingsSection) -> Unit,
    onPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
    hidden: Set<SettingsPage> = emptySet(),
    tabsFocusRequests: Int = 0,
    content: @Composable () -> Unit,
) {
    val tabs = section.pages - hidden
    // Right from a section goes to its tabs, not past them into the page.
    val tabsFocus = remember { FocusRequester() }
    // Each request puts focus on the open page's tab, once the new section's tabs are there.
    LaunchedEffect(tabsFocusRequests) {
        if (tabsFocusRequests > 0) runCatching { tabsFocus.requestFocus() }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(56.dp), modifier = modifier.fillMaxSize().padding(48.dp)) {
        Sections(
            open = section,
            onOpen = onSection,
            rightOf = { entry -> tabsFocus.takeIf { (entry.pages - hidden).size > 1 } },
            modifier = Modifier.width(300.dp).fillMaxHeight(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxHeight().weight(1f)) {
            Text(stringResource(section.title), style = MaterialTheme.typography.headlineSmall)
            if (tabs.size > 1) Tabs(tabs, open = page, onOpen = onPage, focus = tabsFocus)
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

/** The sections; [rightOf] is where Right goes from one, when not into its page. */
@Composable
private fun Sections(
    open: SettingsSection,
    onOpen: (SettingsSection) -> Unit,
    rightOf: (SettingsSection) -> FocusRequester?,
    modifier: Modifier = Modifier,
) {
    // Focus starts on the open section. Until then focus moves by itself (to the first section,
    // e.g. after a language change re-creates the screen) and must not switch sections.
    val current = remember { FocusRequester() }
    var focusPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { current.requestFocus() }
        focusPlaced = true
    }
    val resting = rememberOpenOnRest(onOpen)
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // Coming back from the page lands on the open section, not the nearest one (which would open).
        modifier = modifier.focusRestorer(current).focusGroup(),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 12.dp, bottom = 24.dp),
        )
        SettingsSection.entries.forEach { entry ->
            SettingRow(
                selected = entry == open,
                onClick = { resting.now(entry) },
                leadingContent = {
                    Icon(painterResource(entry.icon), contentDescription = null, modifier = Modifier.size(26.dp))
                },
                headlineContent = { Text(stringResource(entry.title)) },
                modifier = Modifier
                    .then(if (entry == open) Modifier.focusRequester(current) else Modifier)
                    .focusProperties { rightOf(entry)?.let { right = it } }
                    .opensOnRest(
                        resting,
                        entry,
                        isOpen = entry == open,
                        enabled = { focusPlaced },
                        leave = Key.DirectionRight,
                    ),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Tabs(
    tabs: List<SettingsPage>,
    open: SettingsPage,
    onOpen: (SettingsPage) -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val resting = rememberOpenOnRest(onOpen)
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Coming back up from the page lands on the open tab, not the nearest one (which would open).
        modifier = modifier.focusRestorer(focus).focusGroup(),
    ) {
        tabs.forEach { entry ->
            FilterChip(
                selected = entry == open,
                onClick = { resting.now(entry) },
                modifier = Modifier
                    .then(if (entry == open) Modifier.focusRequester(focus) else Modifier)
                    .opensOnRest(resting, entry, isOpen = entry == open, leave = Key.DirectionDown),
            ) { Text(stringResource(entry.title)) }
        }
    }
}
