package dev.lumbina.tv.platform.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.speech.RecognitionService
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.device.DeviceProfile
import dev.lumbina.tv.core.domain.speech.SpeechApp
import dev.lumbina.tv.core.domain.speech.SpeechApps
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The apps that serve speech recognition to others (Lumbina's own service left out), where the
 * remote's audio can be given to them: they record from Android's microphone input, which the
 * profile says has no sound unless Lumbina feeds it.
 */
internal class AndroidSpeechApps @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profile: DeviceProfile,
) : SpeechApps {

    override suspend fun installed(): List<SpeechApp> = withContext(Dispatchers.IO) {
        if (!profile.feedsMicToApps) return@withContext emptyList()
        val packages = context.packageManager
        packages.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            .map { it.serviceInfo }
            .filterNot { it.packageName == context.packageName }
            .map {
                SpeechApp(
                    service = ComponentName(it.packageName, it.name).flattenToString(),
                    label = it.applicationInfo.loadLabel(packages).toString(),
                )
            }
    }
}
