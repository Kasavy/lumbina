package dev.lumbina.tv.platform.system

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.LocaleList
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaActions
import dev.lumbina.tv.core.common.OwnLanguage
import dev.lumbina.tv.core.domain.locale.AppLanguage
import javax.inject.Inject

/**
 * Lumbina's own language. From Android 13 the system keeps it ([LocaleManager]) and redraws
 * Lumbina's screens; before, Lumbina keeps it itself ([OwnLanguage]): each process puts it on its
 * screens and service as they are made, and the running ones hear of a change
 * ([LumbinaActions.LANGUAGE_CHANGED]): the screens are made anew, the service changes in place.
 */
internal class AndroidAppLanguage @Inject constructor(@ApplicationContext private val context: Context) : AppLanguage {

    override fun current(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return OwnLanguage.read(context.filesDir)
        val manager = context.getSystemService(LocaleManager::class.java)
        val locales = manager.applicationLocales
        if (!locales.isEmpty) return locales[0].language
        // Chosen before the device was updated to Android 13: handed to the system once.
        val kept = OwnLanguage.read(context.filesDir) ?: return null
        manager.applicationLocales = LocaleList.forLanguageTags(kept)
        OwnLanguage.write(context.filesDir, null)
        return kept
    }

    override fun set(tag: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Not kept (no room): nothing changes, and the processes are not told of a change.
            if (OwnLanguage.write(context.filesDir, tag)) {
                context.sendBroadcast(Intent(LumbinaActions.LANGUAGE_CHANGED).setPackage(context.packageName))
            }
            return
        }
        val locales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        context.getSystemService(LocaleManager::class.java).applicationLocales = locales
    }
}
