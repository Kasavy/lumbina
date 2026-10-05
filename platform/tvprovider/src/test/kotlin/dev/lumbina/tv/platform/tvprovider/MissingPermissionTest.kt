package dev.lumbina.tv.platform.tvprovider

import android.app.Application
import android.content.pm.PermissionInfo
import android.content.pm.ProviderInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** When Lumbina asks for READ_TV_LISTINGS: only where the system's provider holds the rows and it can be granted. */
@RunWith(RobolectricTestRunner::class)
class MissingPermissionTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val channels = AndroidTvChannels(context) { _, _ -> }

    private fun provider(packageName: String) {
        val info = ProviderInfo().apply {
            authority = "android.media.tv"
            this.packageName = packageName
            name = "$packageName.TvProvider"
        }
        shadowOf(context.packageManager).addOrUpdateProvider(info)
    }

    private fun listings(protection: Int) {
        val info = PermissionInfo().apply {
            name = LISTINGS
            protectionLevel = protection
        }
        shadowOf(context.packageManager).addPermissionInfo(info)
    }

    @Test
    fun `no provider, nothing to ask for`() {
        assertNull(channels.missing())
    }

    @Test
    fun `Lumbina's own companion needs no permission`() {
        provider("dev.lumbina.tv.tvprovider")
        listings(PermissionInfo.PROTECTION_DANGEROUS)
        assertNull(channels.missing())
    }

    @Test
    fun `the system's provider asks for the listings permission until granted`() {
        provider("com.android.providers.tv")
        listings(PermissionInfo.PROTECTION_DANGEROUS)
        assertEquals(LISTINGS, channels.missing())

        shadowOf(context).grantPermissions(LISTINGS)
        assertNull(channels.missing())
    }

    @Test
    fun `a listings permission no user can grant is not asked for`() {
        provider("com.android.providers.tv")
        listings(PermissionInfo.PROTECTION_SIGNATURE)
        assertNull(channels.missing())
    }

    private companion object {
        const val LISTINGS = "android.permission.READ_TV_LISTINGS"
    }
}
