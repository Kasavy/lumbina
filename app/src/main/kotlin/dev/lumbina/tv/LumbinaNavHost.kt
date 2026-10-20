package dev.lumbina.tv

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.ui.LAYERS_ARE_CHEAP
import dev.lumbina.tv.feature.home.HomeRoute
import dev.lumbina.tv.feature.home.HomeTopBarRoute
import dev.lumbina.tv.feature.home.HomeWindows
import dev.lumbina.tv.feature.home.ScreenOnTop
import dev.lumbina.tv.feature.home.WallpaperBackdrop
import dev.lumbina.tv.feature.home.WindowAsk
import dev.lumbina.tv.feature.onboarding.ServicePlateRoute
import dev.lumbina.tv.feature.onboarding.SetupWizardRoute
import dev.lumbina.tv.feature.onboarding.rememberSetupLeft
import dev.lumbina.tv.feature.screensaver.ScreensaverActivity
import dev.lumbina.tv.feature.settings.MediaFilter
import dev.lumbina.tv.feature.settings.MediaLibraryRoute
import dev.lumbina.tv.feature.settings.MediaView
import dev.lumbina.tv.feature.settings.SettingsPage
import dev.lumbina.tv.feature.settings.SettingsRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Lumbina's screens: home, and over it the settings in their panel. Home stays in sight under
 * it and shows at once what is changed there. The view models of both belong to the activity: a
 * screen opened again shows what it last showed from its first frame.
 */
@Composable
internal fun LumbinaNavHost(request: NavRequest?, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    // Counts the times home is sent back to its first tile; saved, so a re-created screen
    // doesn't take an old count for a new request.
    var toStart by rememberSaveable { mutableIntStateOf(0) }
    // Counts the requests to open the "All apps" screen over home.
    var toAllApps by rememberSaveable { mutableIntStateOf(0) }
    // One of home's windows asked for by a button of the remote, until it is closed.
    var window by remember { mutableStateOf<String?>(null) }
    val ask = remember(window) { WindowAsk(window) { window = null } }
    LaunchedEffect(request) {
        if (request == null) return@LaunchedEffect
        val onHome = navController.currentDestination?.route == HOME
        navController.popBackStack(HOME, inclusive = false)
        val page = request.settingsPage
        when {
            page == LumbinaActions.HOME_ALL_APPS -> toAllApps++
            page in LumbinaActions.HOME_WINDOWS -> window = page
            page != null -> navController.navigate("$SETTINGS?$PAGE_ARG=$page")
            request.sendsHomeToStart(onHome) -> toStart++
        }
    }
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    // The route is unknown for the first frame. Re-created over settings (a language change),
    // the activity would take that for home: show it, count it as shown, then fade it away.
    var wasOnHome by rememberSaveable { mutableStateOf(true) }
    val onHome = if (route == null) wasOnHome else route == HOME
    SideEffect { wasOnHome = onHome }
    // Home is where Back ends: left to the system, it would close Lumbina and show what is under
    // it, the firmware's own home screen where Lumbina does not hold the home role.
    BackHandler(enabled = onHome) {}
    // Key presses go to a flow, not to state, so pressing keys doesn't recompose this screen.
    val keyPresses = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val busy = produceState(false) {
        keyPresses.collectLatest {
            value = true
            delay(IDLE_AFTER_KEYS_MILLIS)
            value = false
        }
    }
    val over = remember { ScreenOnTop() }
    Box(
        modifier.fillMaxSize().onPreviewKeyEvent {
            keyPresses.tryEmit(Unit)
            false
        },
    ) {
        // Under the settings' panel home stays in sight; a video holds still there.
        Backdrop(busy = { busy.value || !onHome })
        Home(navController, toStart, toAllApps, visible = onHome, over)
        HomeWindows(ask)
        LumbinaRoutes(navController, checkNotNull(LocalViewModelStoreOwner.current), over.area)
        // Over everything, in a window of its own: at the first start, and when asked back.
        SetupWizardRoute()
    }
}

/** Reads [busy] in its own scope, so key presses recompose only the wallpaper. */
@Composable
private fun Backdrop(busy: () -> Boolean) {
    WallpaperBackdrop(busy = busy())
}

@Composable
private fun Home(
    navController: NavHostController,
    toStart: Int,
    toAllApps: Int,
    visible: Boolean,
    over: ScreenOnTop,
    modifier: Modifier = Modifier,
) {
    HomeRoute(
        modifier = modifier,
        refocus = toStart,
        toAllApps = toAllApps,
        visible = visible,
        over = over,
        openMedia = { bannersOnly ->
            val filter = if (bannersOnly) MediaFilter.Banners else MediaFilter.All
            navController.navigate("$MEDIA?$VIEW_ARG=${MediaView(filter = filter).encode()}")
        },
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeTopBarRoute(
                    onOpenSettings = { navController.navigate(SETTINGS) { launchSingleTop = true } },
                    plates = { ServicePlateRoute() },
                )
            }
        },
    )
}

/**
 * The screens shown over home; on [HOME] itself nothing is. Their view models live in [owner],
 * the activity, not in the screen's own place in the back stack.
 */
@Composable
private fun LumbinaRoutes(navController: NavHostController, owner: ViewModelStoreOwner, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = HOME,
        modifier = modifier,
        enterTransition = { fadeIn(SCREEN_FADE) },
        exitTransition = { fadeOut(SCREEN_FADE) },
    ) {
        composable(HOME) {}
        composable(
            route = "$SETTINGS?$PAGE_ARG={$PAGE_ARG}",
            arguments = listOf(
                navArgument(PAGE_ARG) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { entry ->
            val context = LocalContext.current
            val page = entry.arguments?.getString(PAGE_ARG)
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                val setup = rememberSetupLeft()
                SettingsRoute(
                    initialPage = SettingsPage.entries.firstOrNull { it.name == page },
                    setupLeft = setup.steps,
                    onSetup = setup.open,
                    onPreviewScreensaver = { context.startActivity(Intent(context, ScreensaverActivity::class.java)) },
                    onOpenMedia = { navController.navigate("$MEDIA?$VIEW_ARG=${it.encode()}") },
                )
            }
        }
        composable(
            route = "$MEDIA?$VIEW_ARG={$VIEW_ARG}",
            arguments = listOf(
                navArgument(VIEW_ARG) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { entry ->
            MediaLibraryRoute(MediaView.decode(entry.arguments?.getString(VIEW_ARG)), onBack = {
                navController.popBackStack()
            })
        }
    }
}

private const val HOME = "home"
private const val SETTINGS = "settings"
private const val PAGE_ARG = "page"
private const val MEDIA = "media"
private const val VIEW_ARG = "view"

/** A video wallpaper plays again this long after the last button press. */
private const val IDLE_AFTER_KEYS_MILLIS = 2_000L

/**
 * How one screen gives way to another: a brief fade, where a fading layer is cheap; elsewhere
 * the screens just change (see [LAYERS_ARE_CHEAP]).
 */
private val SCREEN_FADE: FiniteAnimationSpec<Float> = if (LAYERS_ARE_CHEAP) tween(SCREEN_FADE_MILLIS) else snap()
private const val SCREEN_FADE_MILLIS = 150
