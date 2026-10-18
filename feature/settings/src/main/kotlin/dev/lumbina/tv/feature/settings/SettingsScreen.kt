package dev.lumbina.tv.feature.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.health.AccessibilityHealth
import dev.lumbina.tv.core.domain.home.RowSpotlight
import dev.lumbina.tv.core.domain.status.DeviceScreens
import dev.lumbina.tv.core.ui.FreshPressGuard
import dev.lumbina.tv.core.ui.SCRIM_ALPHA
import dev.lumbina.tv.core.ui.SidePanelSurface
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** A page of settings: one row of the root, under its [title] and by its own [icon]. */
enum class SettingsPage(val title: Int, val icon: Int) {
    Rows(R.string.page_rows, R.drawable.ic_page_rows),
    Channels(R.string.settings_channels, R.drawable.ic_page_channels),
    Widgets(R.string.page_widgets, R.drawable.ic_page_widgets),
    Wallpaper(R.string.settings_wallpaper, R.drawable.ic_page_wallpaper),
    Look(R.string.page_look, R.drawable.ic_page_look),
    Voice(R.string.settings_voice, R.drawable.ic_page_voice),
    HomeKey(R.string.page_home_key, R.drawable.ic_page_keys),
    Phone(R.string.settings_phone, R.drawable.ic_page_phone),
    Sleep(R.string.settings_sleep, R.drawable.ic_page_sleep),
    About(R.string.page_about, R.drawable.ic_page_about),
    Access(R.string.page_access, R.drawable.ic_page_access),
    State(R.string.page_state, R.drawable.ic_page_state),
}

/** The root's rows in their groups, a thin line between two: the home screen, the look, input, sleep, the system. */
private enum class RootGroup(val pages: List<SettingsPage>) {
    Home(listOf(SettingsPage.Rows, SettingsPage.Widgets, SettingsPage.Channels)),
    Look(listOf(SettingsPage.Wallpaper, SettingsPage.Look)),
    Input(listOf(SettingsPage.HomeKey, SettingsPage.Voice, SettingsPage.Phone)),
    Sleep(listOf(SettingsPage.Sleep)),
    System(listOf(SettingsPage.Access, SettingsPage.State, SettingsPage.About)),
}

/**
 * Pages this device has no use for: Access needs debugging Lumbina can reach by itself, voice
 * input a remote mic Lumbina hears. And the way out to the device's own settings, where it has any.
 */
@HiltViewModel
class SettingsPagesViewModel @Inject constructor(
    profile: DeviceProfile,
    private val device: DeviceScreens,
    private val listings: TvListingsPermission,
    private val service: AccessibilityHealth,
    spotlight: RowSpotlight,
) : ViewModel() {
    val hidden: Set<SettingsPage> = hiddenPages(localAdb = profile.localAdb, remoteMic = profile.hasRemoteMic)

    /** The row of home being worked on: home dims itself around it, so the panel lays no dimming of its own. */
    val spotlit: StateFlow<String?> = spotlight.key

    val hasDeviceSettings: Boolean = device.hasSettings()

    fun openDeviceSettings(): Boolean = device.openSettings()

    /** The pages where something is wrong now, each with what: said in the root, by the page's name. */
    suspend fun problems(): Map<SettingsPage, Int> = withContext(Dispatchers.IO) {
        buildMap {
            if (listings.missing() != null) put(SettingsPage.Channels, R.string.root_channels_access)
            if (!service.isServiceBound()) put(SettingsPage.HomeKey, R.string.root_service_off)
        }
    }
}

internal fun hiddenPages(localAdb: Boolean, remoteMic: Boolean): Set<SettingsPage> = buildSet {
    if (!localAdb) add(SettingsPage.Access)
    if (!remoteMic) add(SettingsPage.Voice)
}

/**
 * Settings, in the panel at the right over the home screen, which stays in sight and changes at
 * once. The root lists the pages; OK opens one, Back goes up to the root, and from the root closes
 * the settings (that Back is the caller's). [initialPage] opens a page straight away.
 */
@Composable
fun SettingsRoute(
    onPreviewScreensaver: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsPage? = null,
    setupLeft: Int = 0,
    onSetup: () -> Unit = {},
    pages: SettingsPagesViewModel = hiltViewModel(),
) {
    // A page asked for from outside may be one this device doesn't have.
    val first = initialPage?.takeUnless { it in pages.hidden }
    var page by rememberSaveable { mutableStateOf(first) }
    // The root's row left last: where the focus is when the root is shown again.
    var left by rememberSaveable { mutableStateOf(first) }
    // A page inside the open page (one row's settings): its title, shown over the path to it.
    var inside by remember(page) { mutableStateOf<Inside?>(null) }
    // The row whose size is being set on the home screen: the panel steps aside meanwhile.
    var adjusting by remember { mutableStateOf<String?>(null) }
    // The page a link was followed from: Back goes there, not to the root.
    var cameFrom by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    // The root's list as it was left: coming back shows it where it stood, the row left under the focus.
    val rootList = rememberLazyListState()
    val park = remember { FocusRequester() }
    val hold = { runCatching { park.requestFocus() }.let { } }
    BackHandler(enabled = page != null) {
        hold()
        page = cameFrom
        cameFrom = null
    }
    val follow = { to: SettingsPage ->
        if (to !in pages.hidden) {
            hold()
            cameFrom = page
            page = to
        }
    }
    val open = page
    val spotlit by pages.spotlit.collectAsStateWithLifecycle()
    SettingsPanel(open, inside, park, modifier, hidden = adjusting != null, homeDims = spotlit != null) {
        if (open == null) {
            Root(
                hidden = pages.hidden,
                // Read each time the root is shown: a page just left may have put it right.
                problems = produceState(emptyMap(), Unit) { value = pages.problems() }.value,
                setupLeft = setupLeft,
                onSetup = onSetup,
                list = rootList,
                focused = left,
                onOpen = {
                    hold()
                    left = it
                    page = it
                },
                onDevice = deviceSettingsOpener(pages),
            )
        } else {
            val openAccess = { follow(SettingsPage.Access) }.takeUnless { SettingsPage.Access in pages.hidden }
            CompositionLocalProvider(
                LocalSettingsLinks provides SettingsLinks(page = follow, setup = onSetup),
                LocalSubPage provides { inside = it },
                LocalAdjustSize provides { adjusting = it },
            ) {
                SettingsPageContent(open, onPreviewScreensaver, openAccess)
            }
        }
    }
    adjusting?.let { row -> SizeSlider(row, onDone = { adjusting = null }) }
}

/** Opens the device's own settings and says so when the device won't; null where it has none to open. */
@Suppress("ViewModelForwarding")
@Composable
private fun deviceSettingsOpener(pages: SettingsPagesViewModel): (() -> Unit)? {
    if (!pages.hasDeviceSettings) return null
    val context = LocalContext.current
    return {
        if (!pages.openDeviceSettings()) {
            Toast.makeText(context, R.string.settings_device_closed, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * The panel with what is open in it: the root, or [page] under the path back to it. The focus goes
 * to the first row of a page as it opens; the root places its own.
 */
@Composable
private fun SettingsPanel(
    page: SettingsPage?,
    inside: Inside?,
    park: FocusRequester,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    homeDims: Boolean = false,
    content: @Composable () -> Unit,
) {
    val rows = remember { FocusRequester() }
    // Only once the window has the focus: see Root.
    val inFocus = LocalWindowInfo.current.isWindowFocused
    // Once for a page: the window loses the focus to every dialog opened from a row, and getting it
    // back must leave the focus on that row, not send it to the first.
    var placed by remember { mutableStateOf<SettingsPage?>(null) }
    LaunchedEffect(page, inFocus) {
        if (page == null) placed = null
        if (page == null || !inFocus || placed == page) return@LaunchedEffect
        // A page's rows may take a few frames to be laid out.
        repeat(FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { rows.requestFocus() }.getOrDefault(false)) {
                placed = page
                return@LaunchedEffect
            }
        }
    }
    // Opened by OK, the panel appears while OK is still down: its release is not for the first row.
    val guard = remember { FreshPressGuard() }
    val settings = stringResource(R.string.settings_title)
    val pageTitle = page?.let { stringResource(it.title) }
    // Hidden, not gone: what is open in it and where its focus is must be there when it comes back.
    // While a row of home is worked on, home dims itself with a window over that row.
    val scrim = if (hidden || homeDims) 0f else SCRIM_ALPHA
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim))) {
        SidePanelSurface(
            title = inside?.title ?: pageTitle ?: settings,
            path = listOfNotNull(settings.takeIf { page != null }, pageTitle.takeIf { inside != null }),
            // A page inside says what its keys do where that is not what one would guess.
            keyHint = inside?.keys,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer { alpha = if (hidden) 0f else 1f }
                .onPreviewKeyEvent(guard::swallow),
        ) {
            // Where the focus waits while one page's rows give way to another's: left with nothing
            // to stand on, it would jump to the home screen behind for a frame.
            Box(Modifier.focusRequester(park).focusable())
            CompositionLocalProvider(LocalParkFocus provides { runCatching { park.requestFocus() } }) {
                Box(Modifier.fillMaxSize().focusRequester(rows).keepsArrows().focusGroup()) { content() }
            }
        }
    }
}

/**
 * The arrows never take the focus out of a page's rows: above them is only the place the focus
 * is parked at between pages, and ▲ from the first row would leave it there, on nothing in sight.
 */
private fun Modifier.keepsArrows(): Modifier = focusProperties {
    onExit = {
        val arrows = listOf(FocusDirection.Up, FocusDirection.Down, FocusDirection.Left, FocusDirection.Right)
        if (requestedFocusDirection in arrows) cancelFocusChange()
    }
}

/** Where a row of one page leads when what it tells of is set elsewhere: another page, or the setup. */
internal class SettingsLinks(val page: (SettingsPage) -> Unit, val setup: () -> Unit)

@Suppress("CompositionLocalAllowlist")
internal val LocalSettingsLinks = staticCompositionLocalOf { SettingsLinks(page = {}, setup = {}) }

/**
 * A page opened inside a settings page: the panel is headed [title] while it is shown, Back leaves
 * it by [onBack], and the focus starts on its first row. [keys] says what the keys do there, where
 * that is not choosing a row.
 */
@Composable
internal fun InnerPage(title: String, onBack: () -> Unit, keys: String? = null, content: @Composable () -> Unit) {
    SubPage(title, keys)
    val hold = LocalParkFocus.current
    BackHandler {
        hold()
        onBack()
    }
    val rows = remember { FocusRequester() }
    // A row that took the focus itself (the one a deeper page was left for) keeps it.
    val inside = remember { AtomicBoolean() }
    LaunchedEffect(Unit) {
        repeat(INNER_FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (inside.get() || runCatching { rows.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
        }
    }
    Box(Modifier.focusRequester(rows).onFocusChanged { inside.set(it.hasFocus) }.focusGroup()) { content() }
}

private const val INNER_FOCUS_ATTEMPTS = 20

/** Call before the rows under the focus are taken away: the panel holds the focus meanwhile. */
@Suppress("CompositionLocalAllowlist")
internal val LocalParkFocus = staticCompositionLocalOf { {} }

/** A page opened inside the current one: its [title], and its own line about the [keys] if it has one. */
internal data class Inside(val title: String, val keys: String? = null)

/** Tells the panel of a page opened inside the current one; null when it is left. */
@Suppress("CompositionLocalAllowlist")
internal val LocalSubPage = staticCompositionLocalOf<(Inside?) -> Unit> { {} }

/** While composed, the panel is headed [title], under the path through the page this is inside of. */
@Composable
internal fun SubPage(title: String, keys: String? = null) {
    val tell = LocalSubPage.current
    DisposableEffect(title, keys) {
        tell(Inside(title, keys))
        onDispose { tell(null) }
    }
}

/** The root: every page as a row, by group, and last the way out to the device's own settings. */
@Composable
private fun Root(
    hidden: Set<SettingsPage>,
    problems: Map<SettingsPage, Int>,
    setupLeft: Int,
    onSetup: () -> Unit,
    list: LazyListState,
    focused: SettingsPage?,
    onOpen: (SettingsPage) -> Unit,
    onDevice: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val groups = remember(hidden) {
        RootGroup.entries.map { it to (it.pages - hidden) }.filter { it.second.isNotEmpty() }
    }
    val start = focused?.takeUnless { it in hidden } ?: groups.first().second.first()
    val onSetupRow = setupLeft > 0 && focused == null
    val focus = remember { FocusRequester() }
    PlaceRootFocus(focus, onSetupRow)
    // The row comes a moment after the list where Lumbina has just started, and a list keeps its
    // first row in place when one is put in above it: the new row would stay out of sight, the focus with it.
    LaunchedEffect(onSetupRow) { if (onSetupRow) list.scrollToItem(0) }
    // A page opened from outside was not left from its row: the list may never have shown that row.
    LaunchedEffect(Unit) {
        if (!onSetupRow && list.layoutInfo.visibleItemsInfo.none { it.key == start.name }) {
            val above = groups.takeWhile { start !in it.second }
            val lines = above.size + if (setupLeft > 0) 2 else 0
            list.scrollToItem(lines + above.sumOf { it.second.size } + groups[above.size].second.indexOf(start))
        }
    }
    SettingsList(modifier, edges = list) {
        if (setupLeft > 0) {
            item(key = "setup") {
                SettingRow(
                    onClick = onSetup,
                    // What is left to set up comes first, until a page was opened from here.
                    modifier = if (focused == null) Modifier.focusRequester(focus) else Modifier,
                    leadingContent = { RootIcon(R.drawable.ic_page_setup) },
                    headlineContent = { Text(stringResource(R.string.root_setup)) },
                    supportingContent = {
                        Text(
                            pluralStringResource(R.plurals.root_setup_left, setupLeft, setupLeft),
                            color = rowWarning(),
                        )
                    },
                )
            }
            item(key = "line:setup") { GroupLine() }
        }
        groups.forEachIndexed { index, (group, pages) ->
            if (index > 0) item(key = "line:${group.name}") { GroupLine() }
            items(pages.size, key = { pages[it].name }) {
                val page = pages[it]
                SettingRow(
                    onClick = { onOpen(page) },
                    leadingContent = { RootIcon(page.icon) },
                    headlineContent = { Text(stringResource(page.title)) },
                    // Only a problem is said here: how a page is set is the page's own to show.
                    supportingContent = problems[page]?.let { { Text(stringResource(it), color = rowWarning()) } },
                    modifier = if (page == start && !onSetupRow) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
        if (onDevice != null) {
            item(key = "line:device") { GroupLine() }
            item(key = "device") {
                SettingRow(
                    onClick = onDevice,
                    leadingContent = { RootIcon(R.drawable.ic_page_device) },
                    headlineContent = { Text(stringResource(R.string.settings_device)) },
                    supportingContent = { Text(stringResource(R.string.settings_device_hint)) },
                )
            }
        }
    }
}

/**
 * Puts the focus on the root's row behind [focus]. Opened as Lumbina starts, the panel is there
 * before its window has the focus: asked then, the focus stays on the home screen behind, and OK
 * presses a button of the capsule. So it is asked for until the view can take it: once, and once
 * more if the setup row comes in late ([onSetupRow]); not each time a window over this one closes.
 */
@Composable
private fun PlaceRootFocus(focus: FocusRequester, onSetupRow: Boolean) {
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    var placed by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(windowFocused, onSetupRow) {
        if (!windowFocused || placed == onSetupRow) return@LaunchedEffect
        repeat(FOCUS_ATTEMPTS) {
            withFrameNanos { }
            if (runCatching { focus.requestFocus() }.getOrDefault(false)) {
                placed = onSetupRow
                return@LaunchedEffect
            }
        }
    }
}

@Composable
private fun RootIcon(icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp))
}

/** The thin line between two groups of the root: the groups have no captions. */
@Composable
internal fun GroupLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = LINE_ALPHA)),
    )
}

private const val LINE_ALPHA = 0.14f

/**
 * Frames to keep asking for the focus: about three seconds. As Lumbina starts, the window says it
 * has the focus well before its view can take it, and every request until then is refused.
 */
private const val FOCUS_ATTEMPTS = 180
