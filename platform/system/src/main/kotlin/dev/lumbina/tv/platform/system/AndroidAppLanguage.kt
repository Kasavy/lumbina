package dev.lumbina.tv.platform.system

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.locale.AppLanguage
import javax.inject.Inject

/** Per-app language through [LocaleManager] (Android 13+); the system keeps it and redraws Lumbina's screens. */
internal class AndroidAppLanguage @Inject constructor(@ApplicationContext private val context: Context) : AppLanguage {

    override val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun current(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
        return if (locales.isEmpty) null else locales[0].language
    }

    override fun set(tag: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val locales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }
}
