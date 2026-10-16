package dev.lumbina.tv.feature.home

import dev.lumbina.tv.core.domain.app.App
import java.text.Collator
import java.util.Locale

/** Apps by name as [locale]'s language sorts them (ё with е, not after я), letter case aside. */
internal fun appsByName(locale: Locale = Locale.getDefault()): Comparator<App> {
    val names = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return compareBy(names) { it.label }
}
