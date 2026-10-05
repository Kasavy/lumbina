package dev.lumbina.tv.core.domain.sleep

/** Auto sleep after the last button press, in minutes: off, then 1, 2, 3, 4 and 6 hours. */
val AUTO_SLEEP_MINUTES = listOf(null, 60, 120, 180, 240, 360)

/** Idle time before the screensaver, in minutes: off, then 5, 10, 15 and 30. */
val SCREENSAVER_MINUTES = listOf(null, 5, 10, 15, 30)
