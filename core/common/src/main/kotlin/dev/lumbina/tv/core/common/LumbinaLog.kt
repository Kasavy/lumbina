package dev.lumbina.tv.core.common

/**
 * Minimal logging port. Some TV firmware ships with logcat disabled, so the Android
 * implementation writes to a file that can be read over adb.
 */
fun interface LumbinaLog {
    fun log(tag: String, message: String)
}
