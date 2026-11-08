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
@Suppress("TooManyFunctions") // what the service calls it for: keys, windows, its start and end
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
    /**
     * Where Lumbina may stop apps (local adb): the setting, and how. [askForAccess]: while not
     * connected, the ribbon says closing needs the connection; not where that connection is only
     * an option (most TVs and boxes), where it would nag at every showing.
     */
    class Closing(val settings: AccessSettingsRepository, val access: LocalAccess, val askForAccess: Boolean)

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

    /** A button asked to close every app: the same button again soon after closes them. */
    private val closeAllConfirm = ConfirmByRepeat(CONFIRM_MILLIS, SystemClock::uptimeMillis)

    /** A key that is not the ribbon's own closed it: a double Home must not open it again. */
    private val closedByKey = ClosedByKey()
    private var apps: Map<String, App> = emptyMap()
    private val keyboards: Set<String> by lazy {
        service.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
    }
    private val recent = RecentApps(tracked = { it in apps && it !in notListed && it !in keyboards })
    private val overlay = AppSwitcherOverlay(
        context = service,
        texts = {
            AppSwitcherViews.Texts(
                title = service.getString(R.string.switcher_title),
                keys = service.getString(R.string.switcher_hint),
                closeKeys = service.getString(R.string.switcher_hint_close),
                needAccess = service.getString(R.string.switcher_need_access),
                empty = service.getString(R.string.switcher_empty),
                emptyKeys = service.getString(R.string.switcher_hint_empty),
                closing = service.getString(R.string.switcher_closing),
                closeAllKeys = service.getString(R.string.switcher_hint_close_all),
                closeAllCount = { service.getString(R.string.switcher_close_all, it) },
            )
        },
        onPick = { app ->
            log.log(TAG, "switch to ${app.id.packageName}")
            if (!launcher.launch(app.id)) ports.say(service.getString(R.string.switcher_open_failed, app.label))
        },
        onClose = ::close,
        onCloseAll = ::closeAll,
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
            if (overlay.isShowing) showAgain(overlay.selectedIndex)
        }
    }

    /** Shown anew over itself, at the card [card] or still on "Close all": a key being held goes on being held. */
    private fun showAgain(card: Int) =
        overlay.show(listed, card, closeApps, keepKeys = true, toCloseAll = overlay.closeAllSelected)

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
        when {
            closedByKey.consume() -> Unit
            overlay.isShowing -> overlay.hide()
            else -> show()
        }
    }

    private val listed: List<App> get() = recent.recent.mapNotNull(apps::get).take(SHOWN)

    private val closeApps: CloseApps
        get() = when {
            closing == null || !canClose -> CloseApps.No
            connected -> CloseApps.Yes
            closing.askForAccess -> CloseApps.NoAccess
            else -> CloseApps.No
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

                // Shown anew over itself: an OK or an arrow being held goes on being held.
                overlay.isShowing -> showAgain(overlay.selectedIndex)
            }
        }
    }

    /**
     * Stops [targets] one after another, the app that was on screen last: closing it leaves
     * nothing to look at, and Lumbina's home opens once the rest are gone. Those the device
     * would not stop stay on the list and are named.
     */
    private fun closeAll(targets: List<App>) {
        val access = closing?.access ?: return
        scope?.launch {
            val failed = targets.sortedBy { it.id.packageName == inFront }.filterNot { app ->
                val stopped = access.forceStop(app.id.packageName)
                overlay.closed(app)
                if (stopped) recent.forget(app.id.packageName)
                stopped
            }
            log.log(TAG, "close all: ${targets.size - failed.size} stopped, ${failed.size} not")
            save()
            if (failed.isNotEmpty()) {
                ports.say(service.getString(R.string.switcher_close_failed, failed.joinToString { it.label }))
            }
            val closed = targets.map { it.id.packageName } - failed.map { it.id.packageName }.toSet()
            when {
                inFront in closed -> {
                    overlay.hide()
                    ports.home.open(toStart = false)
                }

                overlay.isShowing -> showAgain(0)
            }
        }
    }

    /**
     * A button set to "Close all apps": the first press says how many it would close, and the
     * same again within [CONFIRM_MILLIS] closes them, as "Close all" in the ribbon does. Without
     * the connection to debugging, or with nothing to close, it says so instead.
     */
    fun closeAllByKey() {
        val access = closing?.access ?: return
        val targets = listed
        if (targets.isEmpty()) return ports.say(service.getString(R.string.voice_nothing_to_close))
        val confirmed = closeAllConfirm.confirms()
        scope?.launch {
            if (access.state() != AdbState.Connected) {
                closeAllConfirm.forget()
                return@launch ports.say(service.getString(R.string.voice_cant_close))
            }
            if (!confirmed) {
                ports.say(service.resources.getQuantityString(R.plurals.close_all_again, targets.size, targets.size))
                closeAllConfirm.asked()
                return@launch
            }
            inFront = service.rootInActiveWindow?.packageName?.toString() ?: recent.current
            closeAll(targets)
        }
    }

    val isShowing: Boolean get() = overlay.isShowing

    /** While the switcher shows, it takes the D-pad, OK and Back; true if consumed. */
    fun onKey(event: KeyEvent): Boolean {
        if (!overlay.isShowing) {
            closedByKey.seen(event.keyCode, event.action, event.repeatCount)
            return false
        }
        val taken = overlay.onKey(event)
        if (!taken && !overlay.isShowing) closedByKey.closedBy(event.keyCode)
        return taken
    }

    fun hide() = overlay.hide()

    /** An arrow was released, wherever its release went: a held one stops going on. */
    fun letGo(keyCode: Int) = overlay.letGo(keyCode)

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

        /** As long as the line asking for it stays on screen (the service's notes, RemoteFeatures.NOTE_MILLIS). */
        const val CONFIRM_MILLIS = 4_000L

        /** As many apps as the ribbon shows. */
        const val SHOWN = 8
    }
}
