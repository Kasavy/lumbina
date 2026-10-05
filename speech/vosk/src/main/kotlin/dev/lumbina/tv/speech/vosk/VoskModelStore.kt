package dev.lumbina.tv.speech.vosk

import java.io.File

/**
 * Where speech models live: `<root>/<lang>/`, the app's internal `files/models/` (removed with
 * the app). Models are not bundled in the APK; each is ~35–110 MB. The app downloads them
 * ([ModelInstaller]); debug builds can also receive them over adb via `run-as`
 * (scripts/device/push-model.sh), which cannot reach app-specific external storage — hence
 * internal storage.
 */
class VoskModelStore(internal val root: File) {

    fun directory(language: String): File = File(root, language)

    fun isInstalled(language: String): Boolean = isModel(directory(language))

    /** Partly or fully downloaded archives. */
    internal val downloads: File get() = File(root, ".downloads")

    /** Where an archive is unpacked before it replaces [directory] in one rename. */
    internal fun staging(language: String): File = File(root, ".$language.staging")

    internal fun isModel(dir: File): Boolean = File(dir, "conf/model.conf").isFile && File(dir, "am").isDirectory
}
