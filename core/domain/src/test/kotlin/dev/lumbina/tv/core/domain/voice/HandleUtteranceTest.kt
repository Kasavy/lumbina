package dev.lumbina.tv.core.domain.voice

import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.sleep.SleepSettings
import dev.lumbina.tv.core.domain.text.DeliverText
import dev.lumbina.tv.core.domain.text.Taking
import dev.lumbina.tv.core.domain.text.TextSink
import dev.lumbina.tv.core.testing.FakeSettings
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HandleUtteranceTest {

    private val kinopoisk = App(AppId("ru.kinopoisk.tv", "Main"), "Кинопоиск", isTvApp = true)
    private val launched = mutableListOf<AppId>()
    private val controls = mutableListOf<String>()
    private val typed = mutableListOf<String>()
    private val searched = mutableListOf<String>()
    private var fieldOnScreen = false
    private var inFront: String? = null
    private var stoppable = true

    private val device = object : DeviceControls {
        override fun volume(change: VolumeChange) = controls.add("volume $change")

        override fun media(action: MediaAction) = controls.add("media $action")

        override fun home() = controls.add("home")

        override fun back() = controls.add("back")

        override fun standby() = controls.add("standby")

        override fun appInFront() = inFront

        override suspend fun forceStop(packageName: String) = stoppable && controls.add("stop $packageName")
    }

    private val sleep = FakeSettings(SleepSettings())

    private val field = object : TextSink {
        override val name = "field"

        override suspend fun deliver(text: String): Boolean = fieldOnScreen && typed.add(text)
    }

    private var launches = true

    private fun handler(commands: Boolean = true) = HandleUtterance(
        apps = { listOf(kinopoisk) },
        launcher = { launches && launched.add(it) },
        device = device,
        search = { searched.add(it) },
        deliver = DeliverText(listOf(field)),
        sleep = sleep,
        now = { 1_000L },
        commandsEnabled = { commands },
    )

    @Test
    fun `a phrase whose search did not open is told as such, not as having nowhere to go`() = runTest {
        val search = object : TextSink {
            override val name = "launcher search"

            override suspend fun deliver(text: String) = false

            override suspend fun take(text: String) = Taking.Failed
        }
        val handler = HandleUtterance(
            apps = { emptyList() },
            launcher = { true },
            device = device,
            search = { true },
            deliver = DeliverText(listOf(field, search)),
            sleep = sleep,
            now = { 1_000L },
            commandsEnabled = { true },
        )
        assertEquals(UtteranceOutcome.SearchFailed, handler("котики"))
    }

    @Test
    fun `closes the app named, or the one in front`() = runTest {
        assertEquals(UtteranceOutcome.Closed("Кинопоиск"), handler()("закрой кинопоиск"))
        inFront = "ru.kinopoisk.tv"
        assertEquals(UtteranceOutcome.Done(VoiceCommand.Close(null)), handler()("закрой"))
        assertEquals(listOf("stop ru.kinopoisk.tv", "home", "stop ru.kinopoisk.tv"), controls)
    }

    @Test
    fun `an app in front gives way to Lumbina even where it can't be stopped`() = runTest {
        stoppable = false
        assertEquals(UtteranceOutcome.Failed(VoiceCommand.Close("кинопоиск")), handler()("закрой кинопоиск"))
        inFront = "ru.kinopoisk.tv"
        assertEquals(UtteranceOutcome.Closed("Кинопоиск"), handler()("закрой кинопоиск"))
        assertEquals(listOf("home"), controls)
    }

    @Test
    fun `closing needs an app, neither a launcher in front nor an unknown name`() = runTest {
        assertEquals(UtteranceOutcome.Failed(VoiceCommand.Close(null)), handler()("закрой"))
        assertEquals(UtteranceOutcome.AppNotFound("телеграм"), handler()("закрой телеграм"))
        assertEquals(emptyList<String>(), controls)
    }

    @Test
    fun `opens apps and says which`() = runTest {
        assertEquals(UtteranceOutcome.Opened("Кинопоиск"), handler()("открой кинопоиск"))
        assertEquals(listOf(kinopoisk.id), launched)
        assertEquals(UtteranceOutcome.AppNotFound("телеграм"), handler()("открой телеграм"))
    }

    @Test
    fun `an app that is found but won't start is not reported as missing`() = runTest {
        launches = false
        assertEquals(UtteranceOutcome.NotOpened("Кинопоиск"), handler()("открой кинопоиск"))
    }

    @Test
    fun `types what is not a command`() = runTest {
        fieldOnScreen = true
        assertEquals(UtteranceOutcome.Typed("field"), handler()("котики"))
        fieldOnScreen = false
        assertEquals(UtteranceOutcome.NotDelivered("котики"), handler()("котики"))
    }

    @Test
    fun `find types into a field, or searches without one`() = runTest {
        fieldOnScreen = true
        assertEquals(UtteranceOutcome.Typed("field"), handler()("найди котики"))
        assertEquals(listOf("котики"), typed)
        fieldOnScreen = false
        assertEquals(UtteranceOutcome.Searched("котики"), handler()("найди котики"))
        assertEquals(listOf("котики"), searched)
    }

    @Test
    fun `controls the device`() = runTest {
        handler()("громче")
        handler()("пауза")
        handler()("домой")
        handler()("выключись")
        assertEquals(listOf("volume Up", "media Pause", "home", "standby"), controls)
    }

    @Test
    fun `sets and cancels the sleep timer`() = runTest {
        assertEquals(UtteranceOutcome.SleepSet(30), handler()("выключись через тридцать минут"))
        assertEquals(1_000L + 30 * 60_000L, sleep.current.value.timerEndsAt)
        assertEquals(UtteranceOutcome.SleepSet(null), handler()("отмени таймер"))
        assertEquals(null, sleep.current.value.timerEndsAt)
    }

    @Test
    fun `with commands off everything is typed`() = runTest {
        fieldOnScreen = true
        assertEquals(UtteranceOutcome.Typed("field"), handler(commands = false)("открой кинопоиск"))
        assertEquals(emptyList<AppId>(), launched)
    }
}
