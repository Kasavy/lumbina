package dev.lumbina.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumbina.tv.core.domain.app.InstalledApps
import dev.lumbina.tv.core.domain.app.labelsByPackage
import dev.lumbina.tv.core.domain.status.ConnectivityStatus
import dev.lumbina.tv.core.domain.status.NetworkState
import dev.lumbina.tv.core.domain.status.NotificationFeed
import dev.lumbina.tv.core.ui.WhileUiSubscribed
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A notification as the status bar lists it: the posting app by its label. */
data class NoticeUi(val key: String, val app: String, val title: String, val text: String?)

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
) : ViewModel() {

    val state: StateFlow<StatusUi> = combine(
        connectivity.observe(),
        notifications.hasAccess,
        notifications.notices(),
        installedApps.observe(),
    ) { network, access, notices, apps ->
        val labels = labelsByPackage(apps)
        StatusUi(
            network = network,
            notices = if (access) {
                notices.map {
                    NoticeUi(it.key, labels[it.packageName] ?: it.app, it.title, it.text)
                }
            } else {
                null
            },
        )
    }.stateIn(viewModelScope, WhileUiSubscribed, StatusUi())

    fun openNotice(key: String) {
        notifications.open(key)
    }

    fun clearNotices() = notifications.dismissAll()
}
