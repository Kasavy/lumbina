package dev.lumbina.tv.core.domain.status

/** What can be wrong with the network, worth telling the user. */
enum class NetworkProblem { NoConnection, NoInternet, WeakSignal }

/** What is wrong with this network; null when nothing is. */
fun NetworkState.problem(): NetworkProblem? = when {
    kind == NetworkKind.None -> NetworkProblem.NoConnection
    !online -> NetworkProblem.NoInternet
    kind == NetworkKind.Wifi && (signal ?: NetworkState.MAX_SIGNAL) <= WEAK_SIGNAL -> NetworkProblem.WeakSignal
    else -> null
}

/** Signal bars (of [NetworkState.MAX_SIGNAL]) at or below which the Wi-Fi counts as weak. */
private const val WEAK_SIGNAL = 1
