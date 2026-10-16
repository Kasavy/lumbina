package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.AdbState
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.RecentApps
import dev.lumbina.tv.core.domain.app.RecentAppsRepository
import dev.lumbina.tv.core.domain.remote.LumbinaHome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Recent apps and the switcher (double press on Home). Which app is in front comes from the
 * window changes the accessibility service already receives, so no extra permission is
 * needed. Only launchable apps are listed; Lumbina, the stock launcher and keyboards are not.
 * Switching launches the app, which brings its existing task back where it was. Main thread.
 */
internal class AppSwitching(
    private val service: AccessibilityService,
    private val installedApps: InstalledApps,
    private val launcher: AppLauncher,
    private val notListed: Set<String>,
    private val saved: RecentAppsRepository,
    private val closing: Closing?,
    private val ports: Ports,
    private val log: LumbinaLog,
) {
    /** Where Lumbina may stop apps (local adb): the setting, and how. */
    class Closing(val settings: AccessSettingsRepository, val access: LocalAccess)

    /** [say] puts a line at the bottom of the screen; [home] is where a closed app leaves the user. */
    class Ports(val say: (String) -> Unit, val home: LumbinaHome)

    private var scope: CoroutineScope? = null
    private var lastSaved: List<String>? = null

    /** The saved list is read back once the apps are known; nothing is written before that. */
    private var restored = false
    private var canClose = false

    /** Whether Lumbina's connection to debugging was there when last asked; asked again at each showing. */
    private var connected = true

    /** The app that was on screen when the ribbon opened. */
    private var inFront: String? = null

    /** When a key that is not the ribbon's own closed it: a double Home must not open it again. */
    private var closedByKeyAt = 0L
    private var apps: Map<String, App> = emptyMap()
    private val keyboards: Set<String> by lazy {
        service.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
    }
    private val recent = RecentApps(tracked = { it in apps && it !in notListed && it !in keyboards })
    private val overlay = AppSwitcherOverlay(
        context = service,
        texts = AppSwitcherViews.Texts(
            title = service.getString(R.string.switcher_title),
            keys = service.getString(R.string.switcher_hint),
            closeKeys = service.getString(R.string.switcher_hint_close),
            needAccess = service.getString(R.string.switcher_need_access),
            empty = service.getString(R.string.switcher_empty),
            emptyKeys = service.getString(R.string.switcher_hint_empty),
            closing = service.getString(R.string.switcher_closing),
        ),
        onPick = { app ->
            log.log(TAG, "switch to ${app.id.packageName}")
            if (!launcher.launch(app.id)) ports.say(service.getString(R.string.switcher_open_failed, app.label))
        },
        onClose = ::close,
    )

    /** Keeps the list of launchable apps current (one launcher entry per package). */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        closing?.let { closing ->
            scope.launch { closing.settings.settings.collect { canClose = it.closeFromRecents } }
        }
        scope.launch {
            installedApps.observe().collect { list ->
                apps = list.groupBy { it.id.packageName }.mapValues { (_, entries) -> entries.first() }
                // Once the apps are known: the saved list is filtered by them.
                if (!restored) {
                    recent.restore(saved.recent.first())
                    restored = true
                }
                recent.recent.filterNot { it in apps }.forEach(recent::forget)
                save()
            }
        }
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Dialogs, popups and overlays report plain widget classes; only activities count.
        val className = event.className?.toString().orEmpty()
        if (className.startsWith("android.widget.") || className.startsWith("android.view.")) return
        event.packageName?.toString()?.let(recent::onForeground)
        save()
    }

    /** Writes the list when it changed. */
    private fun save() {
        if (!restored) return
        val list = recent.recent
        if (list == lastSaved) return
        lastSaved = list
        scope?.launch { saved.save(list) }
    }

    fun show() {
        // Ask the screen what is in front: window events can be missed (e.g. returning home).
        val front = service.rootInActiveWindow?.packageName?.toString() ?: recent.current
        inFront = front
        overlay.show(listed, recent.switchTarget(front), closeApps)
        // The ribbon is there at once; what it says about closing follows the connection.
        val access = closing?.access?.takeIf { canClose } ?: return
        scope?.launch {
            val now = access.state() == AdbState.Connected
            if (now == connected) return@launch
            connected = now
            if (overlay.isShowing) overlay.show(listed, overlay.selectedIndex, closeApps, keepKeys = true)
        }
    }

    /** Straight to the app that was on screen before this one, as the ribbon's first OK would go. */
    fun previous() {
        val front = service.rootInActiveWindow?.packageName?.toString() ?: recent.current
        val app = listed.getOrNull(recent.switchTarget(front))
        when {
            // The only recent app is the one in front: there is none before it.
            app == null || app.id.packageName == front -> ports.say(service.getString(R.string.switcher_no_previous))

            !launcher.launch(app.id) -> ports.say(service.getString(R.string.switcher_open_failed, app.label))
        }
    }

    /** The double press that opens the ribbon closes it when it shows: its first press already did. */
    fun toggle() {
        val justClosed = SystemClock.uptimeMillis() - closedByKeyAt < DOUBLE_PRESS_MILLIS
        closedByKeyAt = 0
        if (!justClosed) show()
    }

    private val listed: List<App> get() = recent.recent.mapNotNull(apps::get).take(SHOWN)

    private val closeApps: CloseApps
        get() = when {
            closing == null || !canClose -> CloseApps.No
            connected -> CloseApps.Yes
            else -> CloseApps.NoAccess
        }

    /**
     * Stops [app] and takes it off the list, the selection staying in place; the list loses it
     * even if the ribbon closed before the device answered. Closing the app that was on screen
     * leaves nothing to look at: Lumbina's home opens.
     */
    private fun close(app: App) {
        val access = closing?.access ?: return
        val packageName = app.id.packageName
        scope?.launch {
            val stopped = access.forceStop(packageName)
            log.log(TAG, "close $packageName: ${if (stopped) "stopped" else "no access"}")
            overlay.closed(app)
            if (!stopped) return@launch ports.say(service.getString(R.string.switcher_close_failed, app.label))
            recent.forget(packageName)
            save()
            when {
                packageName == inFront -> {
                    overlay.hide()
                    ports.home.open(toStart = false)
                }

                overlay.isShowing -> overlay.show(listed, overlay.selectedIndex, closeApps)
            }
        }
    }

    /** While the switcher shows, it takes the D-pad, OK and Back; true if consumed. */
    fun onKey(event: KeyEvent): Boolean {
        if (!overlay.isShowing) return false
        val taken = overlay.onKey(event)
        if (!taken && !overlay.isShowing) closedByKeyAt = SystemClock.uptimeMillis()
        return taken
    }

    fun hide() = overlay.hide()

    /** The service is going away. */
    fun dispose() = overlay.dispose()

    /** Debug builds: drives the switcher over adb (see [debugPress]); "list" logs the recent apps. */
    fun debug(command: String) {
        when {
            command == "show" -> show()
            debugPress(command, overlay::onKey) -> Unit
            else -> log.log(TAG, "recent: ${recent.recent}, current: ${recent.current}")
        }
    }

    private companion object {
        const val TAG = "switcher"

        /** As many apps as the ribbon shows. */
        const val SHOWN = 8

        /** Longer than any double press: within it, the press that closed the ribbon is that gesture's first. */
        const val DOUBLE_PRESS_MILLIS = 1_000L
    }
}
