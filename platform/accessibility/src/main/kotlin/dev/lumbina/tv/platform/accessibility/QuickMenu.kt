package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.phone.LocalAddress
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import dev.lumbina.tv.core.domain.phone.phoneRemoteAddress
import dev.lumbina.tv.core.domain.remote.HomeScreen
import dev.lumbina.tv.core.domain.sleep.SLEEP_TIMER_MINUTES
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.sleep.nextSleepTimer
import dev.lumbina.tv.core.domain.sleep.previousSleepTimer
import dev.lumbina.tv.core.domain.sleep.timerMinutesLeft
import dev.lumbina.tv.core.domain.sleep.withTimer
import dev.lumbina.tv.core.domain.status.DeviceScreens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The quick menu (long press on Home), only what the remote doesn't have: all apps, recent apps, the
 * sleep timer right there, the phone remote's code, and Lumbina's settings; under a line, the ways
 * out of Lumbina: the device's own settings and the other home screen (the device's own launcher
 * unless the user chose another). Volume, picture and power are the remote's own buttons. Main thread.
 */
internal class QuickMenu(
    private val service: AccessibilityService,
    private val sleepSettings: SleepSettingsRepository,
    private val ports: Ports,
) {
    /** What the menu's items open or act on. */
    class Ports(
        val otherHome: Flow<HomeScreen?>,
        val launcher: AppLauncher,
        val recentApps: () -> Unit,
        val phone: Flow<PhoneRemoteSettings>,
        val address: LocalAddress,
        val screens: DeviceScreens,
        /** Says something at the bottom of the screen: what did not open. */
        val say: (String) -> Unit,
    )

    private val overlay = QuickMenuOverlay(
        context = service,
        texts = QuickMenuOverlay.Texts(
            title = service.getString(R.string.quick_title),
            pageKeys = service.getString(R.string.quick_page_keys),
        ),
    )
    private var scope: CoroutineScope? = null
    private var sleep = SleepSettings()
    private var otherHome: HomeScreen? = null
    private var phone = PhoneRemoteSettings()

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch { ports.otherHome.collect { otherHome = it } }
        scope.launch { ports.phone.collect { phone = it } }
        scope.launch {
            sleepSettings.settings.collect {
                sleep = it
                if (overlay.isShowing) overlay.refresh()
            }
        }
    }

    fun show() = overlay.show(items())

    val isShowing: Boolean get() = overlay.isShowing

    /** While the menu shows, it takes the D-pad, OK and Back; true if consumed. */
    fun onKey(event: KeyEvent): Boolean = overlay.onKey(event)

    fun hide() = overlay.hide()

    /** Debug builds: drives the menu over adb (see [debugPress]). */
    fun debug(command: String) {
        if (command == "show") show() else debugPress(command, overlay::onKey)
    }

    /** Asked of the package manager once: the device's settings don't come and go. */
    private val hasSettings by lazy { ports.screens.hasSettings() }

    private fun items(): List<QuickMenuOverlay.Item> = listOfNotNull(
        // From any app: Lumbina's "All apps" screen, over home.
        QuickMenuOverlay.Item(service.getString(R.string.quick_all_apps)) {
            openLumbina(LumbinaActions.HOME_ALL_APPS)
        },
        QuickMenuOverlay.Item(service.getString(R.string.quick_recent_apps)) { ports.recentApps() },
        QuickMenuOverlay.Item(
            label = service.getString(R.string.quick_sleep),
            value = { "◀ ${sleepText()} ▶" },
            // The arrows stop at either end of the row of lengths; OK goes round, off after the longest.
            onAdjust = { step ->
                val left = minutesLeft()
                if (step > 0) {
                    setSleepTimer(nextSleepTimer(left) ?: SLEEP_TIMER_MINUTES.last())
                } else if (left != null) {
                    setSleepTimer(previousSleepTimer(left))
                }
            },
            onOk = { setSleepTimer(nextSleepTimer(minutesLeft())) },
            closes = false,
        ),
        phoneItem(),
        QuickMenuOverlay.Item(service.getString(R.string.quick_lumbina_settings)) {
            openLumbina(LumbinaActions.SETTINGS_ROOT)
        },
        QuickMenuOverlay.Item(service.getString(R.string.quick_device_settings), secondary = true) {
            ports.screens.openSettings()
        }.takeIf { hasSettings },
        otherHome?.let { home ->
            QuickMenuOverlay.Item(home.label, secondary = true) {
                if (!ports.launcher.launch(home.id)) ports.say(service.getString(R.string.quick_launcher_failed))
            }
        },
    )

    /**
     * The phone remote: its code and address right in the panel while the page is served; switched
     * off or with no network, the row says so and OK leads to where that is put right.
     */
    private fun phoneItem(): QuickMenuOverlay.Item {
        val address = { phoneRemoteAddress(ports.address.ipv4(), phone.key).takeIf { phone.enabled } }
        return QuickMenuOverlay.Item(
            label = service.getString(R.string.quick_phone),
            value = {
                when {
                    !phone.enabled -> service.getString(R.string.quick_off)
                    address() == null -> service.getString(R.string.quick_no_network)
                    else -> null
                }
            },
            page = {
                address()?.let { url ->
                    QuickMenuOverlay.Page(
                        title = service.getString(R.string.quick_phone),
                        image = qrBitmap(url, service.dp(QuickMenuViews.QR_DP)),
                        // The address with its key: without the key the page opens but takes no command.
                        lines = listOf(url, service.getString(R.string.quick_phone_same_network)),
                    )
                }
            },
        ) { openLumbina(LumbinaActions.SETTINGS_PAGE_PHONE) }
    }

    private fun minutesLeft(): Int? = sleep.timerMinutesLeft(System.currentTimeMillis())

    private fun sleepText(): String =
        minutesLeft()?.let { service.getString(R.string.quick_sleep_left, it) } ?: service.getString(R.string.quick_off)

    private fun setSleepTimer(minutes: Int?) {
        // Shown at once; the stored settings follow a moment later.
        sleep = sleep.withTimer(minutes, System.currentTimeMillis())
        val endsAt = sleep.timerEndsAt
        scope?.launch { sleepSettings.update { it.copy(timerEndsAt = endsAt) } }
    }

    private fun openLumbina(page: String) = service.openLumbina(page)
}
