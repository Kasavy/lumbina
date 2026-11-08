package dev.lumbina.tv.platform.accessibility

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import dev.lumbina.tv.core.common.OwnLanguage

// The same as core/ui's: this module may not depend on it (platform modules see only the domain).

/** This context in Lumbina's own language before Android 13 ([OwnLanguage]); itself otherwise. */
internal fun Context.inOwnLanguage(): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
    val tag = OwnLanguage.read(filesDir) ?: return this
    val config = Configuration(resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
    return createConfigurationContext(config)
}

/** The chosen language put on this context's resources in place, for the service, which outlives a change. */
@Suppress("DEPRECATION")
internal fun Context.applyOwnLanguage() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    val locales = OwnLanguage.read(filesDir)?.let(LocaleList::forLanguageTags)
        ?: Resources.getSystem().configuration.locales
    LocaleList.setDefault(locales)
    val config = Configuration(resources.configuration).apply { setLocales(locales) }
    resources.updateConfiguration(config, resources.displayMetrics)
}
