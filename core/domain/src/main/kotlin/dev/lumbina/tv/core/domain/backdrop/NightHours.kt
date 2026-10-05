package dev.lumbina.tv.core.domain.backdrop

/** Whether [hour] (0..23) is in the evening or night, when the wallpaper may be dimmed. */
fun isNightHour(hour: Int): Boolean = hour >= EVENING_HOUR || hour < MORNING_HOUR

private const val EVENING_HOUR = 22
private const val MORNING_HOUR = 7
