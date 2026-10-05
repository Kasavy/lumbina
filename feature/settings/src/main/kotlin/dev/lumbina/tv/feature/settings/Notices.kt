package dev.lumbina.tv.feature.settings

/** One entry of NOTICE's list of others' work: what it is, and its license with its holder. */
internal data class Component(val name: String, val license: String)

/**
 * The "Third-party components" list of the NOTICE file, which the APK carries (assets/legal): an
 * entry starts with "- ", the lines indented under it are its license, and a blank line ends the
 * list. The screen and the file thus say the same.
 */
internal fun thirdPartyComponents(notice: String): List<Component> {
    val entries = notice.lineSequence()
        .dropWhile { it.trim() != COMPONENTS_HEADING }
        .drop(1)
        .dropWhile { it.isBlank() }
        .takeWhile { it.isNotBlank() }
    val components = mutableListOf<Component>()
    for (line in entries) {
        if (line.startsWith(ENTRY)) {
            components += Component(line.removePrefix(ENTRY).trim(), "")
        } else if (components.isNotEmpty()) {
            val last = components.removeAt(components.lastIndex)
            components += last.copy(license = "${last.license} ${line.trim()}".trim())
        }
    }
    return components
}

/** Where the app's build puts NOTICE (`CopyLegalTexts` in app/build.gradle.kts). */
internal const val NOTICE_ASSET = "legal/NOTICE"

private const val COMPONENTS_HEADING = "Third-party components:"
private const val ENTRY = "- "
