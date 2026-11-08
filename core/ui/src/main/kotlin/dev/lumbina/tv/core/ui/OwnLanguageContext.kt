package dev.lumbina.tv.core.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import dev.lumbina.tv.core.common.OwnLanguage

/**
 * This context in Lumbina's own language ([OwnLanguage]), for a screen or a service as it is made
 * (`attachBaseContext`); itself from Android 13, which keeps the language per app, or with none chosen.
 */
fun Context.inOwnLanguage(): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
    val tag = OwnLanguage.read(filesDir) ?: return this
    val config = Configuration(resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
    return createConfigurationContext(config)
}

/**
 * Puts the chosen language (the device's with none) on this context's resources in place, and on the
 * process as its default: for what outlives a change (the app itself), before Android 13 only.
 * Android has no other way for resources already made; the call is old, but these versions have it.
 */
@Suppress("DEPRECATION")
fun Context.applyOwnLanguage() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    val locales = OwnLanguage.read(filesDir)?.let(LocaleList::forLanguageTags)
        ?: Resources.getSystem().configuration.locales
    LocaleList.setDefault(locales)
    val config = Configuration(resources.configuration).apply { setLocales(locales) }
    resources.updateConfiguration(config, resources.displayMetrics)
}
