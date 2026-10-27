package dev.lumbina.tv.platform.tvprovider

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.channels.ChannelCensus
import dev.lumbina.tv.core.domain.device.PackageStates
import javax.inject.Inject

/**
 * Reads the provider's channels and preview programs whole, before anything is left out for the
 * home screen. Only what Lumbina may read: a system provider keeps other apps' rows from an app
 * without its permission. Where Lumbina TV Provider stands in, its version and log too.
 */
internal class TvProviderCensus @Inject constructor(
    @ApplicationContext private val context: Context,
    private val packages: PackageStates,
) : ChannelCensus {

    override fun take(): List<String> {
        val census = ProviderCensus()
        val failed = listOfNotNull(
            read(
                AndroidTvChannels.CHANNELS,
                arrayOf(Columns.PACKAGE, Columns.BROWSABLE, Columns.DISPLAY_NAME),
                census::channel,
            ),
            read(AndroidTvChannels.PREVIEW_PROGRAMS, arrayOf(Columns.PACKAGE, Columns.BROWSABLE), census::program),
        )
        val rows = census.lines().ifEmpty { if (failed.isEmpty()) listOf("no rows") else emptyList() }
        // A provider that is on the device but turned off reads as none: the package tells.
        return listOf("$SYSTEM_PROVIDER, the system's provider: ${packages.of(SYSTEM_PROVIDER)}") + rows + failed +
            companion()
    }

    /**
     * Where Lumbina's own provider stands in, its version and what it logged of the apps' writes
     * and refusals: an app that never shows up there never reached it.
     */
    @Suppress("TooGenericExceptionCaught") // whatever a provider throws is a line of the report
    private fun companion(): List<String> {
        val pm = context.packageManager
        val provider = pm.resolveContentProvider(AndroidTvChannels.AUTHORITY, 0)?.packageName
        if (provider != AndroidTvChannels.COMPANION) return emptyList()
        val version = companionVersion(pm) ?: return emptyList()
        val log = try {
            context.contentResolver.call(AndroidTvChannels.CHANNELS, COMPANION_LOG, null, null)
                ?.getString(COMPANION_LOG)
                ?.let(::companionLogLines)
                // A companion of another key doesn't take this Lumbina for its own.
                ?: listOf(
                    if (version <
                        LOG_SINCE
                    ) {
                        "its log: kept to itself before version $LOG_SINCE"
                    } else {
                        "its log: not given"
                    },
                )
        } catch (e: RuntimeException) {
            listOf("its log: not read: $e")
        }
        return listOf("Lumbina TV Provider, version $version") + log
    }

    /** Null, or why the table was not read: one table failing must not take the other's count with it. */
    @Suppress("TooGenericExceptionCaught") // whatever a provider throws is a line of the report
    private fun read(uri: Uri, columns: Array<String>, each: (TvRow) -> Unit): String? = try {
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            val row = CursorRow(cursor)
            while (cursor.moveToNext()) each(row)
        }
        null
    } catch (e: RuntimeException) {
        "${uri.lastPathSegment} not read: $e"
    }

    private companion object {
        const val SYSTEM_PROVIDER = "com.android.providers.tv"

        /** The companion's first version that gives Lumbina its log. */
        const val LOG_SINCE = 2L
    }
}
