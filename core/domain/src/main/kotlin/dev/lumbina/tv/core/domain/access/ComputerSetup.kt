package dev.lumbina.tv.core.domain.access

import dev.lumbina.tv.core.domain.health.AccessibilityServiceList

/**
 * The whole setup from a computer, for the setup's own steps where Lumbina can't turn its service
 * on by itself: connecting to [address] (a placeholder when unknown), the [permissions] Lumbina
 * would give itself over its own connection ([LocalAccess.computerCommands]), then [ourService]
 * put on the list of [enabledServices], the others kept.
 */
object ComputerSetup {
    fun commands(
        address: String?,
        permissions: List<String>,
        enabledServices: String?,
        ourService: String,
    ): List<String> {
        val services = AccessibilityServiceList.withoutOurs(enabledServices, ourService) + ourService
        return listOf("adb connect ${address ?: "<IP>"}") + permissions +
            adbShell("settings put secure enabled_accessibility_services '${AccessibilityServiceList.join(services)}'")
    }

    /**
     * The device's shell [command] run from a computer. One with a quoted value (a list of
     * components, whose inner classes' names have a "$") goes whole in double quotes: the
     * computer's shell takes those off and leaves the inner quotes to the device's.
     */
    fun adbShell(command: String): String = if ('\'' in command) "adb shell \"$command\"" else "adb shell $command"
}
