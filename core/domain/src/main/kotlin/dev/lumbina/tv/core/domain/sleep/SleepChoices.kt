package dev.lumbina.tv.core.domain.sleep

/** Auto sleep after the last button press, in minutes: off, then 1, 2, 3, 4 and 6 hours. */
val AUTO_SLEEP_MINUTES = listOf(null, 60, 120, 180, 240, 360)

/** Idle time before the screensaver, in minutes: off, then 1, 5, 10, 15 and 30. */
val SCREENSAVER_MINUTES = listOf(null, 1, 5, 10, 15, 30)

/** How long a photo stays on the screensaver, in minutes. */
val SCREENSAVER_CHANGE_MINUTES = listOf(1, 3, 5, 15)
