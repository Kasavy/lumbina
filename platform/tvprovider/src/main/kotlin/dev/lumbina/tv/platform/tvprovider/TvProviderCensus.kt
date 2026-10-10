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
 * without its permission.
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
            read(AndroidTvChannels.PREVIEW_PROGRAMS, arrayOf(Columns.PACKAGE), census::program),
        )
        val rows = census.lines().ifEmpty { if (failed.isEmpty()) listOf("no rows") else emptyList() }
        // A provider that is on the device but turned off reads as none: the package tells.
        return listOf("$SYSTEM_PROVIDER, the system's provider: ${packages.of(SYSTEM_PROVIDER)}") + rows + failed
    }

    private companion object {
        const val SYSTEM_PROVIDER = "com.android.providers.tv"
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
}
