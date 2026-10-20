package dev.lumbina.tv.platform.tvprovider

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.lumbina.tv.core.common.LumbinaLog
import dev.lumbina.tv.core.domain.access.CompanionApp
import dev.lumbina.tv.core.domain.channels.ChannelCensus
import dev.lumbina.tv.core.domain.channels.ChannelFeed
import dev.lumbina.tv.core.domain.channels.ProgramCard
import dev.lumbina.tv.core.domain.channels.ProgramLauncher
import dev.lumbina.tv.core.domain.channels.TvChannels
import dev.lumbina.tv.core.domain.channels.TvListingsPermission
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Reads the TV Provider (`android.media.tv`) and follows its change notifications. Apps publish
 * in bursts (a channel, then ten programs), hence the debounce before re-reading.
 */
@Singleton
internal class AndroidTvChannels @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) : TvChannels,
    TvListingsPermission {

    private val resolver: ContentResolver get() = context.contentResolver

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val noted = ConcurrentHashMap<String, String>()

    /**
     * Logs what a reading (named by the first argument) gave unless its last reading gave the same:
     * several screens read the same rows, and an app may rewrite its "continue watching" while it plays.
     */
    private val note = { what: String, found: String ->
        if (noted.put(what, found) != found) log.log(TAG, found)
    }

    override fun provider(): String? = context.packageManager.resolveContentProvider(AUTHORITY, 0)?.packageName

    override fun channels(): Flow<List<ChannelFeed>> = observe {
        val channels = query(CHANNELS, Columns.CHANNEL) { channelOf(it, ::logoUri) }
        val programs = query(PREVIEW_PROGRAMS, Columns.PREVIEW_PROGRAM) { row ->
            row.long(Columns.CHANNEL_ID)?.let { channel ->
                programOf(row)?.let { ChannelProgram(channel, it, row.weight()) }
            }
        }
        feedsOf(channels, programs).also { feeds ->
            note(
                "rows",
                "${provider()}: ${channels.size} channels, ${programs.size} programs, ${feeds.size} rows to show",
            )
        }
    }

    /**
     * The system's provider gives other apps' channels and "continue watching" to a holder of
     * READ_TV_LISTINGS, a permission the user grants (seen on Android TV 9, Mi Box 4); without
     * it only Lumbina's own rows. Lumbina's companion provider needs nothing.
     */
    override fun missing(): String? {
        if ((provider() ?: return null) == COMPANION) return null
        // Only where the user can grant it: a firmware may declare it signature-only.
        val askable = try {
            val info = context.packageManager.getPermissionInfo(READ_TV_LISTINGS, 0)
            @Suppress("DEPRECATION") // getProtection() needs API 28; the base bits are the same
            info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE == PermissionInfo.PROTECTION_DANGEROUS
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val granted = context.checkSelfPermission(READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED
        return READ_TV_LISTINGS.takeIf { askable && !granted }
    }

    override fun onGranted() {
        refreshes.tryEmit(Unit)
    }

    override fun watchNext(): Flow<List<ProgramCard>> = observe {
        query(WATCH_NEXT, Columns.WATCH_NEXT) { row -> programOf(row)?.let { it to row.long(Columns.ENGAGED_AT) } }
            .sortedByDescending { it.second ?: 0L }
            .map { it.first }
            .also { note("next", "${provider()}: ${it.size} to continue watching") }
    }

    /**
     * Empty while there is no provider; [refreshes] makes it look again, so the rows come when one
     * is installed (Lumbina's companion, see [TvProviderCompanion]).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observe(read: () -> List<T>): Flow<List<T>> = refreshes
        .onStart { emit(Unit) }
        .map { isAvailable() }
        .distinctUntilChanged()
        .onEach { available -> if (!available) note("provider", "no TV provider on this device") }
        .flatMapLatest { available -> if (available) observeProvider(read) else flowOf(emptyList()) }

    private fun <T> observeProvider(read: () -> List<T>): Flow<List<T>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Uri.parse("content://$AUTHORITY"), true, observer)
        trySend(Unit)
        val refreshing = launch { refreshes.collect { trySend(Unit) } }
        awaitClose {
            refreshing.cancel()
            resolver.unregisterContentObserver(observer)
        }
    }
        .afterBursts()
        .map { read() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /** The first reading at once; later ones wait for a burst of changes to end. */
    @OptIn(FlowPreview::class)
    private fun Flow<Unit>.afterBursts(): Flow<Unit> = flow {
        var first = true
        emitAll(debounce { if (first) 0L else DEBOUNCE_MILLIS }.onEach { first = false })
    }

    private fun <T> query(uri: Uri, projection: Array<String>, parse: (TvRow) -> T?): List<T> = try {
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val row = CursorRow(cursor)
            buildList { while (cursor.moveToNext()) parse(row)?.let(::add) }
        }.orEmpty()
    } catch (e: SecurityException) {
        log.log(TAG, "no access to $uri: $e")
        emptyList()
    } catch (e: IllegalArgumentException) {
        // A system provider rejects columns it does not have (older Android versions).
        log.log(TAG, "query $uri failed: $e")
        emptyList()
    }

    private fun logoUri(channelId: Long): String = "content://$AUTHORITY/channel/$channelId/logo"

    private fun TvRow.weight(): Long = long(Columns.WEIGHT) ?: 0L

    companion object {
        const val AUTHORITY = "android.media.tv"
        val CHANNELS: Uri = Uri.parse("content://$AUTHORITY/channel")
        val PREVIEW_PROGRAMS: Uri = Uri.parse("content://$AUTHORITY/preview_program")
        private const val TAG = "channels"
        private const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        const val COMPANION = "dev.lumbina.tv.tvprovider"
        private const val DEBOUNCE_MILLIS = 500L
        private val WATCH_NEXT: Uri = Uri.parse("content://$AUTHORITY/watch_next_program")
    }
}

internal class CursorRow(private val cursor: Cursor) : TvRow {
    override fun string(column: String): String? =
        cursor.getColumnIndex(column).takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getString)

    override fun long(column: String): Long? = cursor.getColumnIndex(column)
        .takeIf { it >= 0 && !cursor.isNull(it) }
        ?.let { runCatching { cursor.getLong(it) }.getOrNull() }
}

/**
 * Opens a program through its `intent_uri`, kept inside the app that published it (an app may
 * not use Lumbina to start another app's components); without one, the app itself.
 */
internal class AndroidProgramLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: LumbinaLog,
) : ProgramLauncher {

    override fun open(card: ProgramCard): Boolean {
        val intent = card.intentUri?.let(::parse)?.apply {
            setPackage(card.packageName)
            selector = null
            flags = 0 // none of the row's own flags, such as URI permission grants
        } ?: context.packageManager.getLeanbackLaunchIntentForPackage(card.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(card.packageName)
            ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            log.log(TAG, "open ${card.packageName} ${card.id} failed: $e")
            false
        } catch (e: SecurityException) {
            log.log(TAG, "open ${card.packageName} ${card.id} not allowed: $e")
            false
        }
    }

    private fun parse(uri: String): Intent? = try {
        Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
    } catch (e: URISyntaxException) {
        log.log(TAG, "bad intent_uri: $e")
        null
    }

    private companion object {
        const val TAG = "channels"
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface TvProviderModule {
    @Binds fun channels(impl: AndroidTvChannels): TvChannels

    @Binds fun listingsPermission(impl: AndroidTvChannels): TvListingsPermission

    @Binds fun census(impl: TvProviderCensus): ChannelCensus

    @Binds @IntoSet
    fun companion(impl: TvProviderCompanion): CompanionApp

    @Binds fun launcher(impl: AndroidProgramLauncher): ProgramLauncher
}
