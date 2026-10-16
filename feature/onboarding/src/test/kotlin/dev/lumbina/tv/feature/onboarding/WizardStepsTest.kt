package dev.lumbina.tv.feature.onboarding

import dev.lumbina.tv.core.domain.speech.ModelState
import org.junit.Assert.assertEquals
import org.junit.Test

class WizardStepsTest {

    private val nothingDone = SetupStatus(
        serviceRunning = false,
        voice = VoiceSetup(mapOf("ru" to ModelState.Downloading(1, 2)), 2),
        channels = "permission",
        noticesMissing = true,
    )

    @Test
    fun `a fresh install is welcomed and led through all that is left`() {
        assertEquals(
            listOf(
                WizardStep.Welcome,
                WizardStep.Service,
                WizardStep.Channels,
                WizardStep.Notices,
                WizardStep.Voice,
                WizardStep.Done,
            ),
            wizardSteps(nothingDone, languageFirst = false, again = false),
        )
    }

    @Test
    fun `the language is asked before the welcome where the device's says nothing`() {
        val steps = wizardSteps(SetupStatus(serviceRunning = false), languageFirst = true, again = false)
        assertEquals(listOf(WizardStep.Language, WizardStep.Welcome, WizardStep.Service, WizardStep.Done), steps)
    }

    @Test
    fun `asked back, the setup opens at its steps with nobody to welcome`() {
        val left = SetupStatus(serviceRunning = true, noticesMissing = true)
        val steps = wizardSteps(left, languageFirst = true, again = true)
        assertEquals(listOf(WizardStep.Notices, WizardStep.Done), steps)
        assertEquals(listOf(WizardStep.Done), wizardSteps(SetupStatus(serviceRunning = true), false, again = true))
    }
}
