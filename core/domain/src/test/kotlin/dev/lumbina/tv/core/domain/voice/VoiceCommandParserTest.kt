package dev.lumbina.tv.core.domain.voice

import dev.lumbina.tv.core.domain.voice.VoiceCommand.Back
import dev.lumbina.tv.core.domain.voice.VoiceCommand.Close
import dev.lumbina.tv.core.domain.voice.VoiceCommand.Home
import dev.lumbina.tv.core.domain.voice.VoiceCommand.Media
import dev.lumbina.tv.core.domain.voice.VoiceCommand.OpenApp
import dev.lumbina.tv.core.domain.voice.VoiceCommand.Search
import dev.lumbina.tv.core.domain.voice.VoiceCommand.Sleep
import dev.lumbina.tv.core.domain.voice.VoiceCommand.Volume
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The phrase corpus: how people ask, as the recognizers write it (lowercase, numbers in words). */
class VoiceCommandParserTest {

    private val corpus: List<Pair<String, VoiceCommand?>> = listOf(
        // Open an app
        "открой кинопоиск" to OpenApp("кинопоиск"),
        "запусти вк видео" to OpenApp("вк видео"),
        "включи ютуб" to OpenApp("ютуб"),
        "открой смарт тьюб" to OpenApp("смарт тьюб"),
        "зайди в настройки" to OpenApp("настройки"),
        "люмен открой рутуб пожалуйста" to OpenApp("рутуб"),
        "open youtube" to OpenApp("youtube"),
        "launch kinopoisk" to OpenApp("kinopoisk"),
        "go to settings" to OpenApp("settings"),
        // Search
        "найди котики" to Search("котики"),
        "найди фильм интерстеллар" to Search("фильм интерстеллар"),
        "поищи обзор айфона" to Search("обзор айфона"),
        "search for cats" to Search("cats"),
        "find lofi music" to Search("lofi music"),
        // Volume
        "громче" to Volume(VolumeChange.Up),
        "сделай громче" to Volume(VolumeChange.Up),
        "погромче" to Volume(VolumeChange.Up),
        "прибавь звук" to Volume(VolumeChange.Up),
        "тише" to Volume(VolumeChange.Down),
        "сделай потише" to Volume(VolumeChange.Down),
        "убавь громкость" to Volume(VolumeChange.Down),
        "выключи звук" to Volume(VolumeChange.Mute),
        "без звука" to Volume(VolumeChange.Mute),
        "включи звук" to Volume(VolumeChange.Unmute),
        "volume up" to Volume(VolumeChange.Up),
        "louder" to Volume(VolumeChange.Up),
        "turn it down" to Volume(VolumeChange.Down),
        "mute" to Volume(VolumeChange.Mute),
        "unmute" to Volume(VolumeChange.Unmute),
        // Playback
        "пауза" to Media(MediaAction.Pause),
        "поставь на паузу" to Media(MediaAction.Pause),
        "стоп" to Media(MediaAction.Pause),
        "продолжи" to Media(MediaAction.Play),
        "продолжить просмотр" to Media(MediaAction.Play),
        "сними с паузы" to Media(MediaAction.Play),
        "следующая серия" to Media(MediaAction.Next),
        "включи следующую серию" to Media(MediaAction.Next),
        "дальше" to Media(MediaAction.Next),
        "предыдущее видео" to Media(MediaAction.Previous),
        "pause" to Media(MediaAction.Pause),
        "play" to Media(MediaAction.Play),
        "resume" to Media(MediaAction.Play),
        "next episode" to Media(MediaAction.Next),
        "previous" to Media(MediaAction.Previous),
        // Navigation
        "домой" to Home,
        "на главный экран" to Home,
        "go home" to Home,
        "назад" to Back,
        "вернись назад" to Back,
        "go back" to Back,
        // Sleep
        "выключись через тридцать минут" to Sleep(30),
        "выключи через час" to Sleep(60),
        "выключи через полтора часа" to Sleep(90),
        "выключись через два часа" to Sleep(120),
        "таймер сна на сорок пять минут" to Sleep(45),
        "усни через двадцать пять минут" to Sleep(25),
        "выключи через полчаса" to Sleep(30),
        "выключи через 45 минут" to Sleep(45),
        "выключись через два с половиной часа" to Sleep(150),
        "выключи через два часа с половиной" to Sleep(150),
        "выключи через семнадцать минут" to Sleep(17),
        "выключи через одиннадцать минут" to Sleep(11),
        "выключи через два часа тридцать минут" to Sleep(150),
        "выключи через час и пятнадцать минут" to Sleep(75),
        "выключи через 2 часа 30" to Sleep(150),
        "выключи через два часа тридцать" to Sleep(150),
        "выключи через час пожалуйста" to Sleep(60),
        "выключи через сто двадцать минут" to Sleep(120),
        "sleep in a hundred minutes" to Sleep(100),
        "sleep in one hundred and twenty minutes" to Sleep(120),
        "turn off in one hour and a half" to Sleep(90),
        "отмени таймер" to Sleep(null),
        "не выключайся" to Sleep(null),
        "выключись" to Sleep(0),
        "выключи проектор" to Sleep(0),
        "turn off in thirty minutes" to Sleep(30),
        "sleep in an hour" to Sleep(60),
        "turn off in an hour and a half" to Sleep(90),
        "set a sleep timer for twenty minutes" to Sleep(20),
        "turn off in two and a half hours" to Sleep(150),
        "turn off in thirteen minutes" to Sleep(13),
        "sleep in nineteen minutes" to Sleep(19),
        "cancel the sleep timer" to Sleep(null),
        "timer ten minutes" to Sleep(10),
        "set a timer for 10 minutes" to Sleep(10),
        "таймер десять минут" to Sleep(10),
        "поставь таймер на двадцать минут" to Sleep(20),
        "стоп таймер" to Sleep(null),
        "stop the timer" to Sleep(null),
        // Close an app
        "закрой рутуб" to Close("рутуб"),
        "закрой" to Close(null),
        "закрой приложение" to Close(null),
        "close smarttube" to Close("smarttube"),
        "turn off" to Sleep(0),
        // Dictation: typed, not commands
        "котики" to null,
        "интерстеллар" to null,
        "как приготовить борщ" to null,
        "новости" to null,
        "funny cats compilation" to null,
        "" to null,
    )

    @Test
    fun `understands the corpus`() {
        val wrong = corpus.mapNotNull { (phrase, expected) ->
            val actual = VoiceCommandParser.parse(phrase)
            "\"$phrase\": expected $expected, got $actual".takeIf { actual != expected }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test
    fun `ignores punctuation, case and yo`() {
        assertEquals(OpenApp("кинопоиск"), VoiceCommandParser.parse("Открой, Кинопоиск!"))
        assertEquals(Media(MediaAction.Next), VoiceCommandParser.parse("Следующая серия"))
        assertEquals(OpenApp("ежик"), VoiceCommandParser.parse("открой ёжик"))
    }
}
