package dev.lumbina.tv.core.common

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Lumbina's own interface language where Android has none per app (before Android 13): a language
 * tag in a file of the app's files folder, which every process of Lumbina reads as it starts and
 * puts on its screens; [LumbinaActions.LANGUAGE_CHANGED] tells the running ones. Android 13 and
 * later keep the language themselves, and the file is not used there.
 */
object OwnLanguage {
    const val FILE = "own_language"

    /** The chosen language, or null for the device's. */
    fun read(filesDir: File): String? = try {
        File(filesDir, FILE).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: IOException) {
        null
    }

    /**
     * Keeps [tag] (null: the device's language, the file goes); false when it could not be kept
     * (no room). Written aside and then put in place, so a process starting meanwhile never reads
     * half of it.
     */
    fun write(filesDir: File, tag: String?): Boolean {
        val file = File(filesDir, FILE)
        if (tag == null) return file.delete() || !file.exists()
        val aside = File(filesDir, "$FILE.new")
        return try {
            aside.writeText(tag)
            Files.move(
                aside.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
            true
        } catch (_: IOException) {
            aside.delete()
            false
        }
    }
}
