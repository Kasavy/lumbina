package dev.lumbina.tv.platform.system

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import dev.lumbina.tv.core.domain.text.YouTubeApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidYouTubeAppsTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val packages = shadowOf(context.packageManager)

    private val youTubeLink = Intent(Intent.ACTION_VIEW, youTubeSearchLink("a")).addCategory(Intent.CATEGORY_BROWSABLE)
    private val anySite =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)

    private fun install(packageName: String, label: String, vararg opens: Intent) {
        val app = ApplicationInfo().apply {
            this.packageName = packageName
            nonLocalizedLabel = label
        }
        packages.installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                applicationInfo = app
            },
        )
        val handler = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                this.packageName = packageName
                name = "$packageName.Main"
                applicationInfo = app
            }
        }
        opens.forEach { packages.addResolveInfoForIntent(it, handler) }
    }

    @Test
    fun `the apps that open a YouTube link, without browsers and Lumbina, the official one first`() = runTest {
        install("org.client", "A client", youTubeLink)
        install("com.google.android.youtube.tv", "YouTube", youTubeLink)
        install("org.browser", "Browser", youTubeLink, anySite)
        install(context.packageName, "Lumbina", youTubeLink)
        assertEquals(
            listOf(YouTubeApp("com.google.android.youtube.tv", "YouTube"), YouTubeApp("org.client", "A client")),
            AndroidYouTubeApps(context).installed(),
        )
    }

    @Test
    fun `no app for YouTube is an empty list`() = runTest {
        install("org.browser", "Browser", youTubeLink, anySite)
        assertEquals(emptyList<YouTubeApp>(), AndroidYouTubeApps(context).installed())
    }

    @Test
    fun `the list is kept until a package changes`() = runTest {
        val apps = AndroidYouTubeApps(context)
        install("org.client", "A client", youTubeLink)
        assertEquals(listOf("org.client"), apps.installed().map { it.packageName })

        // Installed behind the system's back: nothing says a package changed, so the list stays.
        install("org.other", "Other", youTubeLink)
        assertEquals(listOf("org.client"), apps.installed().map { it.packageName })

        packages.addChangedPackage(0, "org.other")
        assertEquals(listOf("org.client", "org.other"), apps.installed().map { it.packageName })
    }
}
