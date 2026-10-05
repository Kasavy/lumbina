package dev.lumbina.tv.platform.wallpaper

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.RemoteException
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.backdrop.WallpaperItem
import dev.lumbina.tv.core.domain.backdrop.WallpaperKind
import dev.lumbina.tv.core.domain.backdrop.WallpaperPluginInfo
import dev.lumbina.tv.core.domain.backdrop.WallpaperPlugins
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper

/**
 * Hosts Projectivy wallpaper plugins: finds their services by the plugin action, binds one to ask
 * for its wallpapers, then lets it go. Plugin ids are `package/service`.
 */
internal class ProjectivyPlugins @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) : WallpaperPlugins {

    override fun installed(): List<WallpaperPluginInfo> = services().map { info ->
        WallpaperPluginInfo(
            id = "${info.packageName}/${info.name}",
            name = info.metaData?.getString(META_NAME) ?: info.loadLabel(context.packageManager).toString(),
            settingsActivity = settingsOf(info),
        )
    }

    override suspend fun wallpapers(id: String): List<WallpaperItem> {
        val component = ComponentName.unflattenFromString(id) ?: return emptyList()
        return withTimeoutOrNull(BIND_TIMEOUT_MILLIS) { ask(component) }.orEmpty()
    }

    override fun openSettings(id: String): Boolean {
        val info = services().firstOrNull { "${it.packageName}/${it.name}" == id } ?: return false
        val name = settingsOf(info) ?: return false
        return try {
            context.startActivity(Intent().setClassName(info.packageName, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            log.log(TAG, "settings of $id: $e")
            false
        }
    }

    /** The settings activity the plugin names in its metadata, as a full class name. */
    private fun settingsOf(info: ServiceInfo): String? = info.metaData?.getString(META_SETTINGS)
        ?.let { if (it.startsWith(".")) info.packageName + it else it }

    private fun services(): List<ServiceInfo> = context.packageManager
        .queryIntentServices(Intent(ACTION), PackageManager.GET_META_DATA)
        .map { it.serviceInfo }

    private suspend fun ask(component: ComponentName): List<WallpaperItem> {
        var connection: ServiceConnection? = null
        try {
            val binder = suspendCancellableCoroutine<IBinder?> { cont ->
                val waiting = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        if (cont.isActive) cont.resume(binder)
                    }

                    override fun onServiceDisconnected(name: ComponentName) = Unit

                    override fun onNullBinding(name: ComponentName) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onBindingDied(name: ComponentName) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
                if (bind(component, waiting)) {
                    connection = waiting
                } else {
                    log.log(TAG, "cannot bind ${component.flattenToShortString()}")
                    cont.resume(null)
                }
            } ?: return emptyList()
            return withContext(Dispatchers.IO) { fetch(component, IWallpaperProviderService.Stub.asInterface(binder)) }
        } finally {
            connection?.let { runCatching { context.unbindService(it) } }
        }
    }

    /** False also when the plugin guards its service with a permission Lumbina lacks (a [SecurityException]). */
    private fun bind(component: ComponentName, connection: ServiceConnection): Boolean = try {
        context.bindService(Intent(ACTION).setComponent(component), connection, Context.BIND_AUTO_CREATE)
    } catch (e: SecurityException) {
        log.log(TAG, "${component.flattenToShortString()}: $e")
        false
    }

    private fun fetch(component: ComponentName, service: IWallpaperProviderService): List<WallpaperItem> = try {
        service.getWallpapers(Event.TimeElapsed).orEmpty().mapNotNull(::itemOf).also { items ->
            val first = items.firstOrNull()?.let { "${it.kind} ${it.uri.take(LOGGED_URI_CHARS)}" }
            log.log(TAG, "${component.packageName}: ${items.size} wallpapers, first $first")
        }
    } catch (e: RemoteException) {
        log.log(TAG, "${component.flattenToShortString()}: $e")
        emptyList()
    } catch (@Suppress("TooGenericExceptionCaught") e: RuntimeException) {
        // A plugin's own exceptions come back across the binder as runtime ones.
        log.log(TAG, "${component.flattenToShortString()}: $e")
        emptyList()
    }

    private fun itemOf(wallpaper: Wallpaper): WallpaperItem? {
        val kind = when (wallpaper.type) {
            Wallpaper.IMAGE, Wallpaper.DRAWABLE, Wallpaper.ANIMATED_DRAWABLE -> WallpaperKind.Image
            Wallpaper.VIDEO -> WallpaperKind.Video
            Wallpaper.COLOR -> WallpaperKind.Color
            else -> return null // Lottie animations: not supported
        }
        return WallpaperItem(wallpaper.uri, kind, wallpaper.title).takeIf {
            wallpaper.uri.isNotBlank()
        }
    }

    private companion object {
        const val TAG = "wallpaper"
        const val ACTION = "tv.projectivy.plugin.WALLPAPER_PROVIDER"
        const val META_NAME = "name"
        const val META_SETTINGS = "settingsActivity"
        const val BIND_TIMEOUT_MILLIS = 20_000L
        const val LOGGED_URI_CHARS = 120
    }
}
