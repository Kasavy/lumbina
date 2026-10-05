package dev.lumbina.tv.platform.packages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.app.App
import dev.lumbina.tv.core.domain.app.AppId
import dev.lumbina.tv.core.domain.app.InstalledApps
import java.text.Collator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

/**
 * TV apps (LEANBACK_LAUNCHER) plus phone apps (LAUNCHER) — TV launchers usually hide the
 * latter. An app with both entries counts as a TV app. Lumbina itself is excluded. One list per
 * process, shared by everyone who observes it, queried off the main thread.
 */
@Singleton
internal class AndroidInstalledApps @Inject constructor(@ApplicationContext private val context: Context) :
    InstalledApps {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val apps: Flow<List<App>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate()
        .map { query() }
        .flowOn(Dispatchers.IO)
        .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), replay = 1)

    override fun observe(): Flow<List<App>> = apps

    private fun query(): List<App> {
        val pm = context.packageManager
        val tv = pm.activities(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val tvPackages = tv.mapTo(HashSet()) { it.activityInfo.packageName }
        val phone = pm.activities(Intent.CATEGORY_LAUNCHER)
            .filter { it.activityInfo.packageName !in tvPackages }
        // Install and update times of every package in one call.
        val packages = pm.getInstalledPackages(0).associateBy { it.packageName }
        return (
            tv.map { it.toApp(pm, packages, isTv = true) } +
                phone.map { it.toApp(pm, packages, isTv = false) }
            )
            .filter { it.id.packageName != context.packageName }
            .distinctBy { it.id }
            .sortedWith(compareBy(Collator.getInstance().apply { strength = Collator.SECONDARY }) { it.label })
    }

    private fun PackageManager.activities(category: String): List<ResolveInfo> =
        queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun ResolveInfo.toApp(pm: PackageManager, packages: Map<String, PackageInfo>, isTv: Boolean) = App(
        id = AppId(activityInfo.packageName, activityInfo.name),
        label = loadLabel(pm).toString(),
        isTvApp = isTv,
        installedAt = packages[activityInfo.packageName]?.firstInstallTime ?: 0L,
        updatedAt = packages[activityInfo.packageName]?.lastUpdateTime ?: 0L,
    )
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
