package dev.lumbina.tv.platform.accessibility

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.edit
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.LocalAccess

/**
 * Over Lumbina's own adb ([LocalAccess]): grants the microphone permission a speech recognizer
 * must hold, and makes [LumbinaRecognitionService] the system's recognizer once. Should the user
 * pick another one later, that choice stays. Without adb access nothing changes; the next start
 * tries again.
 */
internal class RecognizerSetup(
    private val context: Context,
    private val access: LocalAccess,
    private val log: LumbinaLog,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    suspend fun run() {
        if (!micGranted()) {
            access.grantRecordAudio()
            log.log(TAG, "microphone permission for the recognizer: ${if (micGranted()) "granted" else "not granted"}")
        }
        val ours = ComponentName(context, LumbinaRecognitionService::class.java).flattenToShortString()
        val current = Settings.Secure.getString(context.contentResolver, SETTING)
        when {
            current == ours -> prefs.edit { putBoolean(CLAIMED, true) }

            // Claimed before: the user has picked another since.
            prefs.getBoolean(CLAIMED, false) -> Unit

            access.setSpeechRecognizer(ours) -> {
                prefs.edit { putBoolean(CLAIMED, true) }
                log.log(TAG, "the system's speech recognizer: $ours (was $current)")
            }
        }
    }

    private fun micGranted() =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "voice"
        const val PREFS = "speech_for_apps"
        const val CLAIMED = "recognizer_claimed"
        const val SETTING = "voice_recognition_service"
    }
}
