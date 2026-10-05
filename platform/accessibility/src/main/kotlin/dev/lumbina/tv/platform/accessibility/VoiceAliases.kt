package dev.lumbina.tv.platform.accessibility

/**
 * Common words for kinds of apps whose label says something else. A name for one app is the
 * user's to give: "Voice name" in the app's menu on the home screen.
 */
internal val VOICE_ALIASES: Map<String, List<String>> = mapOf(
    "настройки" to listOf("settings"),
    "браузер" to listOf("browser", "chrome"),
    "магазин" to listOf("store", "rustore", "aptoide", "play store"),
)
