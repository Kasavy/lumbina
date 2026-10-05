package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.AccessSettingsRepository
import dev.lumbina.tv.core.domain.access.LocalAccess
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.RecentApps
import dev.lumbina.tv.core.domain.app.RecentAppsRepository
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
    private val log: LumbinaLog,
) {
    /** Where Lumbina may stop apps (local adb): the setting, and how. */
    class Closing(val settings: AccessSettingsRepository, val access: LocalAccess)

    private var scope: CoroutineScope? = null
    private var lastSaved: List<String>? = null

    /** The saved list is read back once the apps are known; nothing is written before that. */
    private var restored = false
    private var canClose = false
    private var apps: Map<String, App> = emptyMap()
    private val keyboards: Set<String> by lazy {
        service.getSystemService(InputMethodManager::class.java).inputMethodList.map { it.packageName }.toSet()
    }
    private val recent = RecentApps(tracked = { it in apps && it !in notListed && it !in keyboards })
    private val overlay = AppSwitcherOverlay(
        context = service,
        strings = AppSwitcherOverlay.Strings(
            title = service.getString(R.string.switcher_title),
            hint = service.getString(R.string.switcher_hint),
            closeHint = service.getString(R.string.switcher_hint_close),
            empty = service.getString(R.string.switcher_empty),
        ),
        onPick = { app ->
            log.log(TAG, "switch to ${app.id.packageName}")
            launcher.launch(app.id)
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
        val inFront = service.rootInActiveWindow?.packageName?.toString() ?: recent.current
        overlay.show(recent.recent.mapNotNull(apps::get), recent.switchTarget(inFront), canClose)
    }

    /** Stops [app] and takes it off the list, the selection staying in place. */
    private fun close(app: App) {
        val access = closing?.access ?: return
        val packageName = app.id.packageName
        scope?.launch {
            val stopped = access.forceStop(packageName)
            log.log(TAG, "close $packageName: ${if (stopped) "stopped" else "no access"}")
            if (!stopped || !overlay.isShowing) return@launch
            recent.forget(packageName)
            save()
            overlay.show(recent.recent.mapNotNull(apps::get), overlay.selectedIndex, canClose)
        }
    }

    /** While the switcher shows, it takes the D-pad, OK and Back; true if consumed. */
    fun onKey(event: KeyEvent): Boolean = overlay.isShowing && overlay.onKey(event)

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
    }
}
