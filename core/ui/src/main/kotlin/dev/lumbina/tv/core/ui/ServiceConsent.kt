package dev.lumbina.tv.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lumbina.tv.core.domain.device.DeviceProfile

/**
 * Says what Lumbina's accessibility service does before the user turns it on, or lets Lumbina
 * turn it on: Google Play asks for that to be shown first, in the app, and it is fair anyway.
 * [ask] holds the step back until the user agrees in [ServiceConsentDialog]; it is asked each
 * time, as the user may change their mind.
 */
class ServiceConsent internal constructor() {
    internal var pending by mutableStateOf<(() -> Unit)?>(null)

    fun ask(step: () -> Unit) {
        pending = step
    }
}

/**
 * What the service does on this device besides the remote's buttons; each is said in the consent
 * only where it is true.
 */
data class ServiceScope(
    /** The remote has a mic Lumbina hears: the service starts voice input and types what was said. */
    val remoteMic: Boolean = false,
    /** Lumbina is the system's speech recognizer for other apps. */
    val speechForApps: Boolean = false,
    /** Apps that record the microphone themselves may be given the remote's audio. */
    val feedsMicToApps: Boolean = false,
    /** Lumbina connects to the device's debugging and sets itself up over it. */
    val localAdb: Boolean = false,
)

/** What the service does on a device of this profile. */
fun DeviceProfile.serviceScope() = ServiceScope(
    remoteMic = hasRemoteMic,
    speechForApps = speechForApps,
    feedsMicToApps = feedsMicToApps,
    localAdb = localAdb,
)

/** The consent's paragraphs for [scope], as string resources: what every device is told, then what this one does. */
internal fun consentParagraphs(scope: ServiceScope): List<Int> = listOfNotNull(
    if (scope.remoteMic) R.string.service_consent_why else R.string.service_consent_why_no_mic,
    if (scope.remoteMic) R.string.service_consent_sees else R.string.service_consent_sees_no_mic,
    R.string.service_consent_speech.takeIf { scope.remoteMic },
    R.string.service_consent_for_apps.takeIf { scope.speechForApps },
    R.string.service_consent_mic_apps.takeIf { scope.feedsMicToApps },
    R.string.service_consent_debugging.takeIf { scope.localAdb },
)

/** What the service sees and does on a device of [scope], a paragraph each: for a page that says it itself. */
@Composable
fun serviceConsentText(scope: ServiceScope): List<String> = consentParagraphs(scope).map { stringResource(it) }

@Composable
fun rememberServiceConsent(): ServiceConsent = remember { ServiceConsent() }

@Composable
fun ServiceConsentDialog(consent: ServiceConsent, scope: ServiceScope, modifier: Modifier = Modifier) {
    val step = consent.pending ?: return
    val paragraphs = consentParagraphs(scope).map { stringResource(it) }
    ConfirmDialog(
        title = stringResource(R.string.service_consent_title),
        text = paragraphs.joinToString("\n\n"),
        confirmLabel = stringResource(R.string.service_consent_agree),
        cancelLabel = stringResource(R.string.service_consent_not_now),
        onConfirm = {
            consent.pending = null
            step()
        },
        onDismiss = { consent.pending = null },
        modifier = modifier,
        // Wide: the text is long, and on a narrow dialog the buttons ended below the screen.
        width = CONSENT_WIDTH,
    )
}

private val CONSENT_WIDTH = 760.dp
