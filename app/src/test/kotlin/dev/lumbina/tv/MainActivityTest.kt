package dev.lumbina.tv

import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import dev.lumbina.tv.feature.onboarding.R as OnboardingR
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    /**
     * The database's invalidation across processes binds Room's own service. Robolectric answers a
     * bind with no binder unless told one, and Room then fails on a background thread, which fails
     * this test when that thread runs before it ends (on a slower machine): it gets the real one.
     */
    @get:Rule(order = 0)
    val roomService = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()

            @Suppress("UNCHECKED_CAST")
            val service = Class.forName(ROOM_SERVICE) as Class<Service>
            val binder = Robolectric.setupService(service).onBind(Intent())
            shadowOf(app).setComponentNameAndServiceForBindService(ComponentName(app, service), binder)
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun showsTheSetupWhileTheServiceIsNotRunning() {
        // Robolectric has no bound accessibility service and no WRITE_SECURE_SETTINGS grant.
        // The device's language, not an English text the test would depend on.
        val title = compose.activity.getString(OnboardingR.string.wizard_start)
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        /** Not on this module's compile classpath: the data module keeps Room to itself. */
        const val ROOM_SERVICE = "androidx.room.MultiInstanceInvalidationService"
    }
}
