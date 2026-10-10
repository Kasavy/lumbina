package dev.lumbina.tv.core.domain.voice

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppLauncher
import dev.lumbina.tv.core.domain.sleep.SleepSettingsRepository
import dev.lumbina.tv.core.domain.sleep.withTimer
import dev.lumbina.tv.core.domain.text.DeliverText
import dev.lumbina.tv.core.domain.text.Delivery
import dev.lumbina.tv.core.domain.text.YouTubeSearch

/** What the device does for voice commands beyond starting apps. Each returns false if it can't. */
interface DeviceControls {
    fun volume(change: VolumeChange): Boolean

    /** Sent to whatever is playing (the active media session). */
    fun media(action: MediaAction): Boolean

    fun home(): Boolean

    fun back(): Boolean

    fun standby(): Boolean

    /** The app in front, unless it is a launcher. */
    fun appInFront(): String?

    /** Stops the app with [packageName] (never a launcher); false where it can't. */
    suspend fun forceStop(packageName: String): Boolean
}

/** What happened to an utterance, for the on-screen feedback. */
sealed interface UtteranceOutcome {
    data class Typed(val via: String) : UtteranceOutcome

    data class NotDelivered(val text: String) : UtteranceOutcome

    /** Said on a launcher, where it is searched for, and the search did not open. */
    data object SearchFailed : UtteranceOutcome

    data class Opened(val app: String) : UtteranceOutcome

    data class AppNotFound(val query: String) : UtteranceOutcome

    data class Closed(val app: String) : UtteranceOutcome

    /** The app was found, but it would not start. */
    data class NotOpened(val app: String) : UtteranceOutcome

    data class Ambiguous(val apps: List<String>) : UtteranceOutcome

    data class Searched(val query: String) : UtteranceOutcome

    /** Standby in [minutes] (0: now); null: the timer was cancelled. */
    data class SleepSet(val minutes: Int?) : UtteranceOutcome

    data class Done(val command: VoiceCommand) : UtteranceOutcome

    data class Failed(val command: VoiceCommand) : UtteranceOutcome
}

/**
 * Turns recognized speech into an action: a command when it is one (and commands are on),
 * otherwise text for the focused field, the app's keyboard or a search ([deliver]).
 * "Find X" types X when a text field is on screen, and searches ([search]) when none is.
 */
class HandleUtterance(
    private val apps: suspend () -> List<App>,
    private val launcher: AppLauncher,
    private val device: DeviceControls,
    private val search: YouTubeSearch,
    private val deliver: DeliverText,
    private val sleep: SleepSettingsRepository,
    private val now: () -> Long,
    private val commandsEnabled: suspend () -> Boolean = { true },
    private val matcher: AppMatcher = AppMatcher(),
) {
    suspend operator fun invoke(text: String): UtteranceOutcome {
        val command = if (commandsEnabled()) VoiceCommandParser.parse(text) else null
        return when (command) {
            null -> type(text)
            is VoiceCommand.OpenApp -> withApp(command.query, ::open)
            is VoiceCommand.Close -> close(command)
            is VoiceCommand.Search -> find(command.query)
            is VoiceCommand.Sleep -> sleepIn(command.minutes)
            is VoiceCommand.Volume -> done(command, device.volume(command.change))
            is VoiceCommand.Media -> done(command, device.media(command.action))
            VoiceCommand.Home -> done(command, device.home())
            VoiceCommand.Back -> done(command, device.back())
        }
    }

    private suspend fun type(text: String): UtteranceOutcome = when (val delivery = deliver.deliver(text)) {
        is Delivery.To -> UtteranceOutcome.Typed(delivery.sink)
        is Delivery.Failed -> UtteranceOutcome.SearchFailed
        Delivery.Nowhere -> UtteranceOutcome.NotDelivered(text)
    }

    /** [act] on the app [query] names, or says it is ambiguous or not found. */
    private suspend fun withApp(query: String, act: suspend (App) -> UtteranceOutcome): UtteranceOutcome =
        when (val match = matcher.match(query, apps())) {
            is AppMatch.Found -> act(match.app)
            is AppMatch.Ambiguous -> UtteranceOutcome.Ambiguous(match.apps.map { it.label })
            AppMatch.None -> UtteranceOutcome.AppNotFound(query)
        }

    private fun open(app: App): UtteranceOutcome =
        if (launcher.launch(app.id)) UtteranceOutcome.Opened(app.label) else UtteranceOutcome.NotOpened(app.label)

    /** The app named, or the one in front. */
    private suspend fun close(command: VoiceCommand.Close): UtteranceOutcome {
        val front = device.appInFront()
        val query = command.query ?: return done(command, front != null && stop(front, front))
        return withApp(query) { app ->
            val closed = stop(app.id.packageName, front)
            if (closed) UtteranceOutcome.Closed(app.label) else UtteranceOutcome.Failed(command)
        }
    }

    /** An app in front gives way to Lumbina even where it can't be stopped. */
    private suspend fun stop(packageName: String, front: String?): Boolean {
        val inFront = packageName == front
        if (inFront) device.home()
        return device.forceStop(packageName) || inFront
    }

    private suspend fun find(query: String): UtteranceOutcome {
        deliver(query)?.let { return UtteranceOutcome.Typed(it) }
        return if (search.search(query)) UtteranceOutcome.Searched(query) else UtteranceOutcome.NotDelivered(query)
    }

    private suspend fun sleepIn(minutes: Int?): UtteranceOutcome {
        if (minutes == 0) {
            return if (device.standby()) {
                UtteranceOutcome.SleepSet(0)
            } else {
                UtteranceOutcome.Failed(VoiceCommand.Sleep(0))
            }
        }
        val at = now()
        sleep.update { it.withTimer(minutes, at) }
        return UtteranceOutcome.SleepSet(minutes)
    }

    private fun done(command: VoiceCommand, ok: Boolean): UtteranceOutcome =
        if (ok) UtteranceOutcome.Done(command) else UtteranceOutcome.Failed(command)
}
