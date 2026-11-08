package dev.lumbina.tv.feature.onboarding

import dev.lumbina.tv.core.domain.access.AdbState
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceActionTest {

    private val adb = SetupStatus(serviceRunning = false, path = ServicePath.LocalAdb, adb = AdbState.NotAllowed)

    @Test
    fun `refused the service, the device's accessibility screen is offered where there is one`() {
        val notKept = adb.copy(connect = ConnectResult.NotKept)
        assertEquals(ServiceAction.Accessibility, serviceAction(notKept, hasAccessibilitySettings = true))
        assertEquals(ServiceAction.Computer, serviceAction(notKept, hasAccessibilitySettings = false))
    }

    @Test
    fun `a service that didn't come on lets Connect be pressed again`() {
        val notOn = adb.copy(connect = ConnectResult.NotOn)
        assertEquals(ServiceAction.Connect, serviceAction(notOn, hasAccessibilitySettings = false))
    }

    @Test
    fun `while connecting the button stays, doing nothing`() {
        val turningOn = adb.copy(connect = ConnectResult.TurningOn)
        assertEquals(ServiceAction.Busy, serviceAction(turningOn, hasAccessibilitySettings = true))
    }

    @Test
    fun `debugging off leads to the developer settings`() {
        val off = adb.copy(adb = AdbState.Off)
        assertEquals(ServiceAction.Developer, serviceAction(off, hasAccessibilitySettings = true))
    }
}
