package dev.lumbina.tv.core.domain.device

import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.remote.KeyMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeviceProfileResolverTest {

    private class Profile(
        override val id: String,
        override val priority: Int,
        private val predicate: (DeviceFingerprint) -> Boolean,
    ) : DeviceProfile {
        override val keyMap = KeyMap { _, _ -> null }
        override val stockLauncher: AppId? = null
        override fun matches(device: DeviceFingerprint) = predicate(device)
    }

    private fun device(vararg packages: String) = object : DeviceFingerprint {
        override val manufacturer = "Acme"
        override val model = "TV_1"
        override fun isInstalled(packageName: String) = packageName in packages
    }

    private val generic = Profile("generic", 0) { true }
    private val acme = Profile("acme", 100) { it.isInstalled("com.acme.tv.home") }

    @Test
    fun `most specific matching profile wins`() {
        val resolver = DeviceProfileResolver(setOf(generic, acme))
        assertEquals("acme", resolver.resolve(device("com.acme.tv.home")).id)
    }

    @Test
    fun `generic profile is the fallback`() {
        val resolver = DeviceProfileResolver(setOf(generic, acme))
        assertEquals("generic", resolver.resolve(device()).id)
    }
}
