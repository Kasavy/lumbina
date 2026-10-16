package dev.lumbina.tv.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.PhotoFolder
import dev.lumbina.tv.core.domain.backdrop.WallpaperSettingsRepository
import dev.lumbina.tv.core.domain.backdrop.WallpaperSource
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettings
import dev.lumbina.tv.core.domain.phone.PhoneRemoteSettingsRepository
import dev.lumbina.tv.core.domain.speech.SpeechKeys
import dev.lumbina.tv.core.domain.speech.SpeechProvider
import dev.lumbina.tv.core.domain.text.DeliverText
import dev.lumbina.tv.core.domain.text.YouTube
import dev.lumbina.tv.core.domain.voice.DeviceControls
import dev.lumbina.tv.core.domain.voice.MediaAction
import dev.lumbina.tv.core.domain.voice.VolumeChange
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The phone remote: runs the page's server while it is switched on and carries out what the page
 * asks — remote buttons, touchpad pointer, text, links, pictures and videos for the wallpaper.
 */
internal class PhoneRemote(
    private val service: AccessibilityService,
    private val settings: PhoneRemoteSettingsRepository,
    private val ports: Ports,
    private val log: LumbinaLog,
) {
    /** What the page acts on; grouped to keep the constructor short. */
    class Ports(
        val controls: DeviceControls,
        val deliver: () -> DeliverText,
        val folder: PhotoFolder,
        val wallpaper: WallpaperSettingsRepository,
        val speechKeys: SpeechKeys,
        val youTube: YouTube,
        /** Home held on the page, as on the remote. */
        val quickMenu: () -> Unit,
        /** Home tapped twice on the page, as on the remote. */
        val appSwitcher: () -> Unit,
        /**
         * A key to Lumbina's own overlays (quick menu, recent apps) while one shows; true if taken.
         * The page's arrows otherwise reach the app underneath: overlays get no D-pad focus.
         */
        val overlayKey: (KeyEvent) -> Boolean,
        /** Holds OK down, where Lumbina can (it takes its own access to the device); null where it can't. */
        val holdOk: (suspend () -> Boolean)? = null,
        /** Whether the remote has a mic Lumbina hears: only then are the speech services' keys of any use. */
        val hasMic: Boolean = false,
    )

    private val cursor by lazy { PhoneCursor(service) }

    /**
     * What the page is told as it opens: the device's name (two devices in a home look alike
     * otherwise), and what this one can do, so the page shows no button that does nothing.
     */
    private val info: () -> String = {
        val name = Settings.Global.getString(service.contentResolver, "device_name") ?: Build.MODEL
        JSONObject()
            .put("ok", true)
            .put("name", name)
            .put("arrows", DPAD_ACTIONS.isNotEmpty())
            .put("holdOk", ports.holdOk != null)
            .put("mic", ports.hasMic)
            .toString()
    }

    /**
     * The last diagnostics report, as the settings kept it for a phone; null while none was saved.
     * Given once: the key it is asked for with is on every QR code of the page (the screensaver's
     * too), and the report holds the log and the list of apps.
     */
    private val report: () -> ByteArray? = {
        File(service.filesDir, PhoneRemoteSettings.REPORT_FILE).takeIf { it.isFile }
            ?.let { file -> file.readBytes().also { file.delete() } }
    }

    /**
     * What a button press answers before the device gets it: taken by one of Lumbina's overlays
     * while it shows (as a down and an up; they work on any Android), or refused when the device
     * can't take arrows. Null: the device handles it.
     */
    private val keyFirst: (String) -> PhoneAnswer? = { button ->
        val code = OVERLAY_KEYS[button]
        val time = SystemClock.uptimeMillis()
        when {
            code != null && ports.overlayKey(KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0)) -> {
                ports.overlayKey(KeyEvent(time, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
                PhoneAnswer.Done
            }

            button in DPAD_BUTTONS && DPAD_ACTIONS.isEmpty() -> PhoneAnswer.Unsupported

            else -> null
        }
    }

    /** Home's gestures on the page, as on the remote: tap, hold, two taps. */
    private val homeGestures: Map<String, () -> Boolean> = mapOf(
        "home" to { ports.controls.home() },
        "homelong" to { true.also { ports.quickMenu() } },
        "homedouble" to { true.also { ports.appSwitcher() } },
    )
    private var server: PhoneRemoteServer? = null

    fun start(scope: CoroutineScope) {
        scope.launch {
            // Only what the server is made of: any other setting changed must not drop the phones connected.
            settings.settings.distinctUntilChangedBy { it.enabled to it.key }.collect { current ->
                server?.stop()
                server = null
                if (current.enabled && current.key.isNotEmpty()) {
                    val port = PhoneRemoteSettings.PORT
                    server = PhoneRemoteServer(port, current.key, ::page, ::handle, log, report, info)
                        .also { it.start(scope) }
                }
            }
        }
    }

    fun stop() {
        server?.stop()
        server = null
        cursor.hide()
    }

    private fun page(): ByteArray = service.resources.openRawResource(R.raw.phone_remote).use { it.readBytes() }

    private suspend fun handle(request: PhoneRequest): PhoneAnswer {
        val button = request.query["b"].orEmpty()
        if (request.command == "key") onMain { keyFirst(button) }?.let { return it }
        return PhoneAnswer.of(
            when (request.command) {
                "ping" -> true
                "key" -> pressed(button)
                "move", "click", "longclick", "scroll" -> onMain { pointer(request.command.orEmpty(), request.query) }
                "text" -> text(request)
                "link" -> request.shortBody()?.let { link -> withContext(Dispatchers.Main) { open(link) } } ?: false
                "upload" -> upload(request)
                "speechkey" -> saveSpeechKey(ports.speechKeys, request.query["p"], request.shortBody(), log)
                else -> false
            },
        )
    }

    /** The touchpad: moves the pointer, clicks and scrolls where it points. */
    private fun pointer(command: String, query: Map<String, String>): Boolean {
        val dy = query["dy"]?.toFloatOrNull() ?: 0f
        return when (command) {
            "move" -> {
                cursor.move(query["dx"]?.toFloatOrNull() ?: 0f, dy)
                true
            }

            "scroll" -> cursor.scroll(dy)

            else -> cursor.click(long = command == "longclick")
        }
    }

    /** A button of the page: OK held goes by Lumbina's own access to the device, the rest by the service. */
    private val pressed: suspend (String) -> Boolean = { button ->
        if (button == "oklong") ports.holdOk?.invoke() ?: false else onMain { key(button) }
    }

    private fun key(button: String): Boolean = homeGestures[button]?.invoke() ?: when (button) {
        "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        "play" -> ports.controls.media(MediaAction.Play)
        "pause" -> ports.controls.media(MediaAction.Pause)
        "playpause" -> ports.controls.media(MediaAction.PlayPause)
        "next" -> ports.controls.media(MediaAction.Next)
        "previous" -> ports.controls.media(MediaAction.Previous)
        "volup" -> ports.controls.volume(VolumeChange.Up)
        "voldown" -> ports.controls.volume(VolumeChange.Down)
        "mute" -> ports.controls.volume(VolumeChange.Mute)
        else -> DPAD_ACTIONS[button]?.let(service::performGlobalAction) ?: false
    }

    private suspend fun text(request: PhoneRequest): Boolean {
        val text = request.shortBody()?.takeIf { it.isNotEmpty() } ?: return false
        return withContext(Dispatchers.Main) { ports.deliver()(text) } != null
    }

    /** A short text body (typed text, a link); null if it is too long to be one. */
    private suspend fun PhoneRequest.shortBody(): String? = withContext(Dispatchers.IO) {
        body.takeIf { contentLength <= MAX_TEXT_BYTES }?.readBytes()?.decodeToString()?.trim()
    }

    /** Opens a link: YouTube ones in the app chosen for it, anything else by the system. */
    private suspend fun open(link: String): Boolean {
        val uri = Uri.parse(link.takeIf { it.contains("://") } ?: "https://$link")
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val youtube = uri.host?.let { it.endsWith("youtube.com") || it == "youtu.be" } == true
        val client = if (youtube) ports.youTube.app()?.let { Intent(intent).setPackage(it.packageName) } else null
        return listOfNotNull(client, intent).any { candidate ->
            try {
                service.startActivity(candidate)
                true
            } catch (e: ActivityNotFoundException) {
                log.log(TAG, "open $link: $e")
                false
            }
        }
    }

    private suspend fun upload(request: PhoneRequest): Boolean {
        val name = request.query["name"].orEmpty()
        if (request.contentLength !in 1..MAX_UPLOAD_BYTES) return false
        val saved = withContext(Dispatchers.IO) { ports.folder.add(name, request.body, request.contentLength) }
        log.log(TAG, "upload \"$name\" (${request.contentLength} bytes): ${if (saved) "saved" else "failed"}")
        if (saved && request.query["wallpaper"] == "1") {
            ports.wallpaper.update {
                it.copy(source = WallpaperSource.MyMedia, mediaChangedAt = System.currentTimeMillis())
            }
        }
        return saved
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    private companion object {
        const val TAG = "phone"
        const val MAX_TEXT_BYTES = 16 * 1024L
        const val MAX_UPLOAD_BYTES = 2L * 1024 * 1024 * 1024

        val DPAD_BUTTONS = setOf("up", "down", "left", "right", "ok")

        val OVERLAY_KEYS = mapOf(
            "up" to KeyEvent.KEYCODE_DPAD_UP,
            "down" to KeyEvent.KEYCODE_DPAD_DOWN,
            "left" to KeyEvent.KEYCODE_DPAD_LEFT,
            "right" to KeyEvent.KEYCODE_DPAD_RIGHT,
            "ok" to KeyEvent.KEYCODE_DPAD_CENTER,
            "back" to KeyEvent.KEYCODE_BACK,
        )

        /** Empty below Android 13, which has no D-pad actions for accessibility services. */
        val DPAD_ACTIONS: Map<String, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mapOf(
                "up" to AccessibilityService.GLOBAL_ACTION_DPAD_UP,
                "down" to AccessibilityService.GLOBAL_ACTION_DPAD_DOWN,
                "left" to AccessibilityService.GLOBAL_ACTION_DPAD_LEFT,
                "right" to AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT,
                "ok" to AccessibilityService.GLOBAL_ACTION_DPAD_CENTER,
            )
        } else {
            emptyMap()
        }
    }
}

/**
 * A cloud recognizer's API key pasted on the page (typing one with the remote is no fun), for the
 * provider named [p]; an empty [key] removes it. The page never gets keys back.
 */
private suspend fun saveSpeechKey(keys: SpeechKeys, p: String?, key: String?, log: LumbinaLog): Boolean {
    val provider = SPEECH_PROVIDERS[p] ?: return false
    if (!keys.set(provider, key ?: return false)) return false
    log.log("phone", if (key.isEmpty()) "speech key removed: $provider" else "speech key set: $provider")
    return true
}

private val SPEECH_PROVIDERS = mapOf("openai" to SpeechProvider.OpenAi, "yandex" to SpeechProvider.Yandex)
