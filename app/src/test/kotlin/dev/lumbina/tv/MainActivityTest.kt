package dev.lumbina.tv

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import dev.lumbina.tv.feature.onboarding.R as OnboardingR
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsSetupCardWhileTheServiceIsNotRunning() {
        // Robolectric has no bound accessibility service and no WRITE_SECURE_SETTINGS grant.
        // The device's language, not an English text the test would depend on.
        val title = compose.activity.getString(OnboardingR.string.setup_one_step)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
