package dev.lumbina.tv.platform.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.app.AppSystemActions
import javax.inject.Inject

internal class AndroidAppSystemActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) : AppSystemActions {

    override val hasDetails: Boolean by lazy {
        val probe = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context.packageName))
        probe.resolveActivity(context.packageManager) != null
    }

    override fun showDetails(packageName: String): Boolean =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(packageName)))

    override fun uninstall(packageName: String): Boolean = start(Intent(Intent.ACTION_DELETE, packageUri(packageName)))

    private fun packageUri(packageName: String) = Uri.fromParts("package", packageName, null)

    /** Some TV firmware leaves out the system screens; report that instead of crashing. */
    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        log.log(TAG, "${intent.action} unavailable: $e")
        false
    }

    private companion object {
        const val TAG = "launcher"
    }
}
