package dev.lumbina.tv.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape

/** A tile's shape: a TV banner's 16:9, width to height. */
const val BANNER_ASPECT = 16f / 9f

/** Round ends at any height: buttons, bars and the messages at the bottom. */
val PillShape = RoundedCornerShape(percent = 50)

/** How dark the screen goes behind a panel, a dialog or what is not being set. */
const val SCRIM_ALPHA = 0.5f

/** How faint what can't be used now stands: the other tiles while one is moved, a row out of reach. */
const val DIMMED_ALPHA = 0.4f

/** How long something just taken off the screen can be put back, on home and in the settings alike. */
const val UNDO_MILLIS = 8_000L

/** What stands between the parts of one line: "App · channel". */
const val LINE_SEPARATOR = " · "

/** The mark of what is chosen, ticked or in order. */
const val TICK = "✓"

/** [parts] as one line, [LINE_SEPARATOR] between them; the missing and empty ones left out. */
fun line(vararg parts: String?): String = parts.filterNot { it.isNullOrEmpty() }.joinToString(LINE_SEPARATOR)
