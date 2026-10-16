package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.labelsByPackage
import dev.lumbina.tv.core.domain.home.HomeAppearanceRepository
import dev.lumbina.tv.core.domain.status.ConnectivityStatus
import dev.lumbina.tv.core.domain.status.DeviceScreens
import dev.lumbina.tv.core.domain.status.NetworkState
import dev.lumbina.tv.core.domain.status.NotificationFeed
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A notification as the status bar lists it: the posting app by its label. */
data class NoticeUi(
    val key: String,
    val app: String,
    val title: String,
    val text: String?,
    val packageName: String = "",
    val canOpen: Boolean = true,
)

/**
 * [network] is null until known (so no "no Wi-Fi" flashes at start); [notices] is null without
 * notification access (then the bell is not shown at all).
 */
data class StatusUi(val network: NetworkState? = null, val notices: List<NoticeUi>? = null)

/** The top bar's status part: the network and other apps' notifications. */
@HiltViewModel
class StatusViewModel @Inject constructor(
    connectivity: ConnectivityStatus,
    installedApps: InstalledApps,
    private val notifications: NotificationFeed,
    private val screens: DeviceScreens,
    appearance: HomeAppearanceRepository,
) : ViewModel() {

    /** Null until first read: the bell is part of the capsule, which is drawn whole. */
    val state: StateFlow<StatusUi?> = combine(
        connectivity.observe(),
        notifications.hasAccess,
        notifications.notices(),
        installedApps.observe(),
        appearance.settings.map { it.mutedNotices }.distinctUntilChanged(),
    ) { network, access, notices, apps, muted ->
        val labels = labelsByPackage(apps)
        StatusUi(
            network = network,
            notices = if (access) {
                notices.filterNot { it.packageName in muted }.map {
                    NoticeUi(it.key, labels[it.packageName] ?: it.app, it.title, it.text, it.packageName, it.canOpen)
                }
            } else {
                null
            },
        )
    }.stateIn<StatusUi?>(viewModelScope, WhileUiSubscribed, null)

    /** Opens the notification and takes it off the list; false when it did not open, and then it stays. */
    fun openNotice(notice: NoticeUi): Boolean = notifications.open(notice.key).also { opened ->
        if (opened) notifications.dismiss(notice.key)
    }

    fun dismissNotice(notice: NoticeUi) = notifications.dismiss(notice.key)

    fun clearNotices() = notifications.dismissAll()

    /** False when the device lets no network settings open. */
    fun openNetwork(): Boolean = screens.openNetwork()
}
