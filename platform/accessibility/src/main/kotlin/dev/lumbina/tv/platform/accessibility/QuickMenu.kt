package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.sleep.nextSleepTimer
import dev.lumbina.tv.core.domain.sleep.timerMinutesLeft
import dev.lumbina.tv.core.domain.sleep.withTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The quick menu (long press on Home), only what the remote doesn't have: recent apps, the
 * sleep timer right there, and control from a phone; below, smaller, the other home screen (the
 * device's own launcher unless the user chose another) and Lumbina's settings. Volume, picture
 * and power are the remote's own buttons. Main thread.
 */
internal class QuickMenu(
    private val service: AccessibilityService,
    private val sleepSettings: SleepSettingsRepository,
    private val ports: Ports,
) {
    /** What the menu's items open or act on. */
    class Ports(val otherHome: Flow<HomeScreen?>, val launcher: AppLauncher, val recentApps: () -> Unit)

    private val overlay = QuickMenuOverlay(
        context = service,
        title = service.getString(R.string.quick_title),
        hint = service.getString(R.string.quick_hint),
    )
    private var scope: CoroutineScope? = null
    private var sleep = SleepSettings()
    private var otherHome: HomeScreen? = null

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch { ports.otherHome.collect { otherHome = it } }
        scope.launch {
            sleepSettings.settings.collect {
                sleep = it
                if (overlay.isShowing) overlay.refresh()
            }
        }
    }

    fun show() = overlay.show(items())

    /** While the menu shows, it takes the D-pad, OK and Back; true if consumed. */
    fun onKey(event: KeyEvent): Boolean = overlay.onKey(event)

    fun hide() = overlay.hide()

    /** Debug builds: drives the menu over adb (see [debugPress]). */
    fun debug(command: String) {
        if (command == "show") show() else debugPress(command, overlay::onKey)
    }

    private fun items(): List<QuickMenuOverlay.Item> = listOfNotNull(
        QuickMenuOverlay.Item(service.getString(R.string.quick_recent_apps), value = { "›" }) {
            ports.recentApps()
        },
        QuickMenuOverlay.Item(
            label = service.getString(R.string.quick_sleep),
            value = { "◀ ${sleepText()} ▶" },
            onAdjust = { step -> setSleepTimer(if (step > 0) nextSleepTimer(minutesLeft()) else null) },
            onOk = { setSleepTimer(nextSleepTimer(minutesLeft())) },
            closes = false,
        ),
        QuickMenuOverlay.Item(service.getString(R.string.quick_phone), value = { "QR ›" }) {
            openLumbinaSettings(LumbinaActions.SETTINGS_PAGE_PHONE)
        },
        otherHome?.let { home ->
            QuickMenuOverlay.Item(home.label, secondary = true) { ports.launcher.launch(home.id) }
        },
        QuickMenuOverlay.Item(service.getString(R.string.quick_lumbina_settings), secondary = true) {
            openLumbinaSettings(LumbinaActions.SETTINGS_PAGE_ROWS)
        },
    )

    private fun minutesLeft(): Int? = sleep.timerMinutesLeft(System.currentTimeMillis())

    private fun sleepText(): String =
        minutesLeft()?.let { service.getString(R.string.quick_sleep_left, it) } ?: service.getString(R.string.quick_off)

    private fun setSleepTimer(minutes: Int?) {
        // Shown at once; the stored settings follow a moment later.
        sleep = sleep.withTimer(minutes, System.currentTimeMillis())
        val endsAt = sleep.timerEndsAt
        scope?.launch { sleepSettings.update { it.copy(timerEndsAt = endsAt) } }
    }

    private fun openLumbinaSettings(page: String) {
        val intent = service.packageManager.getLaunchIntentForPackage(service.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            .putExtra(LumbinaActions.EXTRA_SETTINGS_PAGE, page)
        service.startActivity(intent)
    }
}
