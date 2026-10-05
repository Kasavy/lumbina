package dev.lumbina.tv.core.domain.app

import kotlinx.coroutines.flow.Flow

/**
 * Which apps were on screen lately, most recent first, built from "this package is in front
 * now" signals. Packages [tracked] rejects (the launcher itself, keyboards, system screens)
 * are not listed but still count as being in front, so [switchTarget] knows what is current.
 * Not thread-safe: feed it from one thread.
 */
class RecentApps(private val tracked: (String) -> Boolean, private val capacity: Int = DEFAULT_CAPACITY) {

    private val packages = ArrayDeque<String>()

    /** The package in front now, whether tracked or not. */
    var current: String? = null
        private set

    val recent: List<String> get() = packages.toList()

    fun onForeground(packageName: String) {
        current = packageName
        if (!tracked(packageName)) return
        packages.remove(packageName)
        packages.addFirst(packageName)
        while (packages.size > capacity) packages.removeLast()
    }

    /**
     * Puts back a list saved before a restart (the process doesn't survive standby on some
     * devices), after whatever was seen since; untracked packages are dropped.
     */
    fun restore(saved: List<String>) {
        saved.filter { it !in packages && tracked(it) }.forEach(packages::addLast)
        while (packages.size > capacity) packages.removeLast()
    }

    /** Forgets an app, e.g. one that was uninstalled. */
    fun forget(packageName: String) {
        packages.remove(packageName)
    }

    /**
     * Where a switcher should start: the previous app when the one in front ([inFront], by
     * default the last reported) leads the list — switching back and forth is the common
     * case — otherwise the most recent one.
     */
    fun switchTarget(inFront: String? = current): Int = if (packages.size > 1 && packages.first() == inFront) 1 else 0

    private companion object {
        const val DEFAULT_CAPACITY = 8
    }
}

/** The recent apps' order, kept across restarts; shared by the service's process and the launcher. */
interface RecentAppsRepository {
    val recent: Flow<List<String>>

    suspend fun save(packages: List<String>)
}
