package dev.lumbina.tv.core.domain.voice

/** Something the user asked for by voice, rather than text to type. */
sealed interface VoiceCommand {
    data class OpenApp(val query: String) : VoiceCommand

    /** Stops [query]'s app, or the one in front when null. */
    data class Close(val query: String?) : VoiceCommand

    data class Search(val query: String) : VoiceCommand

    data class Volume(val change: VolumeChange) : VoiceCommand

    data class Media(val action: MediaAction) : VoiceCommand

    data object Home : VoiceCommand

    data object Back : VoiceCommand

    /** Standby in [minutes] (0: now); null cancels a running sleep timer. */
    data class Sleep(val minutes: Int?) : VoiceCommand
}

enum class VolumeChange { Up, Down, Mute, Unmute }

enum class MediaAction { Play, Pause, Next, Previous }

/**
 * Recognizes commands in Russian and English speech-to-text output (lowercase words, no
 * punctuation, numbers spelled out). Anything that is not a command returns null and is typed.
 */
object VoiceCommandParser {

    fun parse(text: String): VoiceCommand? {
        val phrase = normalize(text)
        if (phrase.isEmpty()) return null
        return fixedCommand(phrase) ?: sleepCommand(phrase) ?: closeCommand(phrase) ?: withArgument(phrase)
    }

    private fun fixedCommand(phrase: String): VoiceCommand? = FIXED.firstNotNullOfOrNull { (pattern, command) ->
        command.takeIf { pattern.matches(phrase) }
    }

    private fun sleepCommand(phrase: String): VoiceCommand? {
        if (SLEEP_CANCEL.matches(phrase)) return VoiceCommand.Sleep(null)
        if (SLEEP_NOW.matches(phrase)) return VoiceCommand.Sleep(0)
        val match = SLEEP_IN.matchEntire(phrase) ?: return null
        // Russian or English branch: the duration is the last group that matched.
        return NumberWords.minutes(match.groupValues.last { it.isNotEmpty() })?.let { VoiceCommand.Sleep(it) }
    }

    /** "Close X", or just "close" (or "close the app") for the one in front. */
    private fun closeCommand(phrase: String): VoiceCommand? = CLOSE.matchEntire(phrase)?.let { match ->
        VoiceCommand.Close(match.groupValues.last().trim().takeIf { it.isNotEmpty() && it !in THIS_APP })
    }

    private fun withArgument(phrase: String): VoiceCommand? {
        SEARCH.matchEntire(phrase)?.let { return VoiceCommand.Search(it.groupValues.last().trim()) }
        OPEN.matchEntire(phrase)?.let { return VoiceCommand.OpenApp(it.groupValues.last().trim()) }
        return null
    }

    /** Lowercase, `ё` as `е`, no punctuation, no polite fillers or the launcher's name. */
    internal fun normalize(text: String): String {
        val words = text.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }
        return words.dropWhile { it in FILLERS }.dropLastWhile { it in FILLERS }.joinToString(" ")
    }

    private val FILLERS = setOf("пожалуйста", "please", "люмен", "lumbina", "эй", "hey", "ну")

    private fun r(pattern: String) = Regex(pattern)

    private val FIXED: List<Pair<Regex, VoiceCommand>> = listOf(
        r(
            "(сделай )?(по)?громче( звук)?|прибавь( звук| громкость)?|увеличь (звук|громкость)|" +
                "(turn )?(the )?volume up|louder|turn it up|turn up( the)?( volume)?",
        ) to
            VoiceCommand.Volume(VolumeChange.Up),
        r(
            "(сделай )?(по)?тише( звук)?|убавь( звук| громкость)?|уменьши (звук|громкость)|" +
                "(turn )?(the )?volume down|quieter|turn it down|turn down( the)?( volume)?",
        ) to
            VoiceCommand.Volume(VolumeChange.Down),
        r("(выключи|отключи|убери) звук|без звука|mute( the sound)?") to VoiceCommand.Volume(VolumeChange.Mute),
        r("(включи|верни) звук|unmute") to VoiceCommand.Volume(VolumeChange.Unmute),
        r("пауза|(поставь )?на паузу|стоп|останови( видео)?|pause|stop") to VoiceCommand.Media(MediaAction.Pause),
        r("продолжи(ть)?( просмотр)?|играй|воспроизведи|плей|сними с паузы|play|resume|continue") to
            VoiceCommand.Media(MediaAction.Play),
        r("(включи )?следующ\\p{L}*( серию| серия| видео| трек)?|дальше|next( episode| video)?") to
            VoiceCommand.Media(MediaAction.Next),
        r("(включи )?предыдущ\\p{L}*( серию| серия| видео| трек)?|previous( episode| video)?") to
            VoiceCommand.Media(MediaAction.Previous),
        r("домой|на главн\\p{L}*( экран)?|главн\\p{L}* экран|(go )?home") to VoiceCommand.Home,
        r("назад|вернись( назад)?|(go )?back") to VoiceCommand.Back,
    )

    private val SLEEP_CANCEL =
        r(
            "(отмени|отключи|сбрось|убери|останови|стоп) таймер( сна)?|не выключай(ся)?|" +
                "(cancel|stop)( the)? (sleep )?timer",
        )
    private val SLEEP_NOW = r(
        "(выключи(сь)?|выключить|усни)( (проектор|телевизор|тв|экран))?|спать|" +
            "(turn off|switch off)( the)?( tv| projector)?|go to sleep|sleep",
    )
    private val SLEEP_IN = r(
        "(выключи(сь)?|выключить|усни|засыпай) (через|на) (.+)|" +
            "(turn off|switch off|sleep|go to sleep|sleep timer|set( a)? sleep timer)( for| in) (.+)|" +
            "(поставь |set )?(a )?(sleep )?(таймер|timer)( сна)?( for| in| на| через)? (.+)",
    )

    private val CLOSE = r("(закрой|закрыть|выйди из|close|quit|exit)( (.+))?")
    private val THIS_APP = setOf("приложение", "это", "это приложение", "app", "the app", "this", "this app", "it")

    private val SEARCH = r("(найди|найти|поищи|ищи|поиск|search for|search|find|look for) (.+)")
    private val OPEN = r(
        "(открой|открыть|запусти|запустить|включи|включить|зайди в|перейди в|покажи|" +
            "open|launch|start|run|go to) (.+)",
    )
}
