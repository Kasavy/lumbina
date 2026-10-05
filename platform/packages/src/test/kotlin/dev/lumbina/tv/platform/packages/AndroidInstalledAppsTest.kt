package dev.lumbina.tv.platform.packages

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import dev.lumbina.tv.core.domain.app.AppId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidInstalledAppsTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun install(packageName: String, installedAt: Long, vararg activities: Pair<String, String>) {
        val pm = shadowOf(context.packageManager)
        pm.installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                firstInstallTime = installedAt
                lastUpdateTime = installedAt + 500
                applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
            },
        )
        for ((name, category) in activities) {
            val component = ComponentName(packageName, name)
            pm.addActivityIfNotPresent(component)
            pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(category) })
        }
    }

    @Test
    fun `every activity of a package carries the package's install and update times`() = runBlocking {
        install(
            "com.example.tv",
            1_000,
            "com.example.tv.Main" to Intent.CATEGORY_LEANBACK_LAUNCHER,
            "com.example.tv.Kids" to Intent.CATEGORY_LEANBACK_LAUNCHER,
        )
        install("com.example.phone", 2_000, "com.example.phone.Main" to Intent.CATEGORY_LAUNCHER)

        val apps = AndroidInstalledApps(context).observe().first().associate {
            it.id to (it.installedAt to it.updatedAt)
        }
        assertEquals(1_000L to 1_500L, apps[AppId("com.example.tv", "com.example.tv.Main")])
        assertEquals(1_000L to 1_500L, apps[AppId("com.example.tv", "com.example.tv.Kids")])
        assertEquals(2_000L to 2_500L, apps[AppId("com.example.phone", "com.example.phone.Main")])
    }
}
