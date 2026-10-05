package dev.lumbina.tv.core.domain.health

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Access to the system's enabled-accessibility-services setting. */
interface SecureSettings {
    /** False until the user grants WRITE_SECURE_SETTINGS over adb. */
    val canWrite: Boolean

    fun enabledAccessibilityServices(): String?

    fun setEnabledAccessibilityServices(value: String)

    fun setAccessibilityEnabled()
}

/** Whether our accessibility service is currently bound by the system. */
fun interface AccessibilityHealth {
    fun isServiceBound(): Boolean
}

/**
 * The colon-separated `enabled_accessibility_services` list. Some firmware rewrites it on
 * boot, leaving duplicates or an entry that is listed but never bound.
 */
object AccessibilityServiceList {

    private fun parse(value: String?): List<String> =
        value.orEmpty().split(':').map(String::trim).filter { it.isNotEmpty() && it != "null" }

    /** Other services, de-duplicated, without [ours]. */
    fun withoutOurs(value: String?, ours: String): List<String> = parse(value)
        .filterNot { it.substringBefore('/') == ours.substringBefore('/') }
        .distinct()

    fun join(services: List<String>): String = services.joinToString(":")
}

/**
 * Lets one heal run at a time across Lumbina's processes: the remote's job, the home screen coming
 * to the front and the settings may all try at once.
 */
interface HealLock {
    suspend fun <T> withLock(block: suspend () -> T): T
}

sealed interface HealResult {
    data object AlreadyRunning : HealResult
    data object MissingPermission : HealResult
    data object Reenabled : HealResult
}

/**
 * Brings our accessibility service back when the system lists it but doesn't run it:
 * removing it from the list and adding it again makes the system bind it. A service found
 * unbound gets [settleMillis] first: the system may be binding it right now (e.g. as the
 * device wakes, or after another heal just toggled it), and toggling then leaves it bound twice,
 * every remote key acting twice. The wait and the toggle run under [lock], so a second heal waits
 * for the first and then sees its result.
 */
class HealAccessibilityService(
    private val settings: SecureSettings,
    private val health: AccessibilityHealth,
    private val ourService: String,
    private val lock: HealLock,
    private val toggleDelayMillis: Long = 1_500,
    private val settleMillis: Long = 8_000,
) {
    suspend operator fun invoke(): HealResult {
        if (health.isServiceBound()) return HealResult.AlreadyRunning
        if (!settings.canWrite) return HealResult.MissingPermission
        return lock.withLock { healUnbound() }
    }

    private suspend fun healUnbound(): HealResult {
        delay(settleMillis)
        if (health.isServiceBound()) return HealResult.AlreadyRunning

        val others = AccessibilityServiceList.withoutOurs(settings.enabledAccessibilityServices(), ourService)
        // Once our entry is off it must come back, even if whoever asked for the heal is gone by then.
        withContext(NonCancellable) {
            settings.setEnabledAccessibilityServices(AccessibilityServiceList.join(others))
            delay(toggleDelayMillis)
            settings.setEnabledAccessibilityServices(AccessibilityServiceList.join(others + ourService))
            settings.setAccessibilityEnabled()
        }
        return HealResult.Reenabled
    }
}
