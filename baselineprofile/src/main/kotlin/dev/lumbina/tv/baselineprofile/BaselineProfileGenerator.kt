package dev.lumbina.tv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code run on startup, while scrolling home, in "All apps", menus and settings;
 * release builds ship it so Android compiles those paths ahead of time at install. Regenerate
 * after big UI changes, on an emulator set to English:
 * `ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile`.
 *
 * Never without `ANDROID_SERIAL`: the task runs on every device adb sees, and on each it installs
 * a test build over Lumbina and then **uninstalls Lumbina with all its data**. Disconnecting the
 * other devices is not enough, they come back.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        requireEnglish()
        pressHome()
        allowTvListings()
        startActivityAndWait()
        skipSetup()
        browseHome()
        visitAllApps()
        visitSettings()
    }
}
