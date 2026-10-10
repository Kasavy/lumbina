package dev.lumbina.tv.platform.system

import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.remote.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreensTest {

    private val lumbina = HomeActivity(AppId("dev.lumbina.tv", "dev.lumbina.tv.MainActivity"), "Lumbina", "Lumbina")
    private val fallback =
        HomeActivity(
            AppId("com.android.tv.settings", "com.android.tv.settings.system.FallbackHome"),
            "Settings",
            "Settings",
        )
    private val setup = HomeActivity(AppId("com.google.android.setupwizard", "Setup"), "Setup", "Setup")
    private val stock = HomeActivity(AppId("com.acme.launcher", "com.acme.Launcher"), "com.acme.Launcher", "Acme TV")
    private val other = HomeActivity(AppId("org.other", "org.other.Home"), "Other Launcher", "Other Launcher")

    private fun screens(vararg activities: HomeActivity) =
        homeScreens(activities.toList(), "dev.lumbina.tv", stock.id, "Acme launcher")

    @Test
    fun `the firmware's launcher comes first under the device's name, Lumbina and the stand-ins are left out`() {
        assertEquals(
            listOf(HomeScreen(stock.id, "Acme launcher"), HomeScreen(other.id, "Other Launcher")),
            screens(lumbina, fallback, other, setup, stock),
        )
    }

    @Test
    fun `screens of one name are told apart by the app, then the package, then the activity`() {
        fun page(packageName: String, activity: String, app: String) =
            HomeActivity(AppId(packageName, activity), "Start page", app)

        assertEquals(
            listOf("Start page (Assistant)", "Start page (Widgets)"),
            screens(page("com.a", "com.a.Home", "Assistant"), page("com.b", "com.b.Home", "Widgets")).map { it.label },
        )
        assertEquals(
            listOf("Start page (com.a)", "Start page (com.b)"),
            screens(page("com.a", "com.a.Home", "Acme"), page("com.b", "com.b.Home", "Acme")).map { it.label },
        )
        assertEquals(
            listOf("Start page (Full)", "Start page (Simple)"),
            screens(page("com.a", "com.a.Full", "Acme"), page("com.a", "com.a.Simple", "Acme")).map { it.label },
        )
    }

    @Test
    fun `a name shared only with the firmware's launcher, which goes by the device's, stays as it is`() {
        val twin = HomeActivity(AppId("org.twin", "org.twin.Home"), stock.label, "Twin")
        assertEquals(listOf("Acme launcher", stock.label), screens(stock, twin).map { it.label })
    }
}
