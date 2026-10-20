package dev.lumbina.tv.tvprovider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File
import java.io.FileNotFoundException

/**
 * Stands in for com.android.providers.tv (authority `android.media.tv`) where the device has none.
 * Apps publish channels, preview programs and watch-next programs; each app sees only its own
 * rows, while Lumbina (same signing key) and adb shell see everything. Channels are browsable at
 * once: there is no system UI here to ask the user, and Lumbina lets the user hide channels itself.
 */
class CompanionTvProvider : ContentProvider() {

    private lateinit var store: RowStore

    override fun onCreate(): Boolean {
        val context = requireNotNull(context)
        CompanionLog.init(context)
        store = RowStore(context)
        return true
    }

    override fun getType(uri: Uri): String {
        val kind = uri.pathSegments.firstOrNull() ?: "unknown"
        val item = uri.pathSegments.size > 1
        return "vnd.android.cursor.${if (item) "item" else "dir"}/$kind"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val caller = caller()
        val kind = uri.pathSegments.firstOrNull()?.takeIf { it in KINDS }
            ?: return null.also { CompanionLog.log("$caller insert refused: $uri") }
        val row = ContentValues(values ?: ContentValues())
        row.remove(PACKAGE_NAME) // always the caller's
        if (!store.ownsChannelIn(row, caller)) {
            return null.also {
                CompanionLog.log("$caller insert refused: another app's channel")
            }
        }
        if (kind == CHANNEL) row.put(BROWSABLE, 1)
        val id = store.insert(kind, caller, row)
        val name = row.getAsString(TITLE) ?: row.getAsString(DISPLAY_NAME).orEmpty()
        CompanionLog.log("$caller insert $kind → $id $name")
        return ContentUris.withAppendedId(kindUri(kind), id).also(::notifyChange)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val rows = RowSelection.sort(selected(uri, selection, selectionArgs), sortOrder, ::rowValue)
        val columns = projection?.toList()
            ?: (listOf(ID, PACKAGE_NAME) + rows.flatMap { it.values.keys }).distinct()
        return MatrixCursor(columns.toTypedArray()).apply {
            rows.forEach { row -> addRow(columns.map { column -> rowValue(row, column) }) }
            setNotificationUri(requireNotNull(context).contentResolver, uri)
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val changes = ContentValues(values ?: ContentValues()).apply { remove(PACKAGE_NAME) }
        val owner = visibleOwner()
        if (owner != null && !store.ownsChannelIn(changes, owner)) {
            return 0.also { CompanionLog.log("$owner update refused: another app's channel") }
        }
        val rows = selected(uri, selection, selectionArgs)
        rows.forEach { store.update(it, changes) }
        if (rows.isNotEmpty()) notifyChange(uri)
        return rows.size
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val rows = selected(uri, selection, selectionArgs)
        rows.forEach(store::delete)
        if (rows.isNotEmpty()) {
            CompanionLog.log("${caller()} delete $uri → ${rows.size}")
            notifyChange(uri)
        }
        return rows.size
    }

    /**
     * Channel logos: `channel/<id>/logo`, written by `ChannelLogoUtils.storeChannelLogo`. Only the
     * channel's app may write its logo; anyone may read it (a launcher shows them).
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val writing = mode.contains('w')
        val id = uri.pathSegments.takeIf { it.size == LOGO_PATH_SIZE && it[0] == CHANNEL && it[2] == LOGO }
            ?.get(1)?.toLongOrNull()
            ?.takeIf { !writing || store.rows(CHANNEL, caller(), it).isNotEmpty() }
        val file = id?.let { File(requireNotNull(context).filesDir, "logos/$it") }
            ?.takeIf { writing || it.exists() }
            ?: throw FileNotFoundException(uri.toString())
        val parsedMode = ParcelFileDescriptor.parseMode(mode)
        if (!writing) return ParcelFileDescriptor.open(file, parsedMode)
        file.parentFile?.mkdirs()
        val channel = ContentUris.withAppendedId(kindUri(CHANNEL), id)
        // Readers are told once the app has written the logo and closed the file.
        return ParcelFileDescriptor.open(file, parsedMode, Handler(Looper.getMainLooper())) { notifyChange(channel) }
    }

    /**
     * The end of this provider's log for Lumbina's diagnostics report (`TvProviderCensus` asks for
     * it by [LOG_METHOD]); nothing for other apps, whose writes it names.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != LOG_METHOD || visibleOwner() != null) return null
        return Bundle().apply { putString(LOG_METHOD, CompanionLog.tail(LOG_CHARS)) }
    }

    /**
     * The caller's rows the [uri] (kind, id, channel) and [selection] pick. A selection this
     * provider can't read picks none: better than updating or deleting all of the app's rows.
     */
    private fun selected(uri: Uri, selection: String?, args: Array<out String>?): List<RowStore.Row> {
        val kind = uri.pathSegments.firstOrNull().orEmpty()
        val rows = store.rows(kind, visibleOwner(), itemId(uri)).inChannelOf(uri)
        return RowSelection.select(rows, selection, args, ::rowValue)
            ?: emptyList<RowStore.Row>().also { CompanionLog.log("${caller()} unsupported selection: $selection") }
    }

    /** Null when the caller may see every app's rows, else the caller's package. */
    private fun visibleOwner(): String? {
        val uid = Binder.getCallingUid()
        if (uid == Process.SHELL_UID || uid == Process.myUid()) return null
        val caller = caller()
        val context = requireNotNull(context)
        val isLumbina = caller == LUMBINA_PACKAGE &&
            context.packageManager.checkSignatures(caller, context.packageName) == PackageManager.SIGNATURE_MATCH
        return if (isLumbina) null else caller
    }

    private fun notifyChange(uri: Uri) = requireNotNull(context).contentResolver.notifyChange(uri, null)

    private companion object {
        const val LUMBINA_PACKAGE = "dev.lumbina.tv"
        val KINDS = setOf(CHANNEL, "program", "preview_program", "watch_next_program", "recorded_program")
        const val BROWSABLE = "browsable"
        const val TITLE = "title"
        const val DISPLAY_NAME = "display_name"
        const val LOGO = "logo"
        const val LOGO_PATH_SIZE = 3
        const val LOG_METHOD = "lumbina_log"

        /** Some hundred lines: the reply waits in a binder buffer of a megabyte that all calls share. */
        const val LOG_CHARS = 48 * 1024
    }
}

private const val AUTHORITY = "android.media.tv"
private const val CHANNEL = "channel"
private const val CHANNEL_ID = "channel_id"

/**
 * Whether a program's `channel_id` in [values], if any, names a channel of [owner]'s. Lumbina drops
 * such programs when reading too (`feedsOf`, where the reason is); this keeps them out of the store.
 */
private fun RowStore.ownsChannelIn(values: ContentValues, owner: String): Boolean {
    val channel = values.getAsLong(CHANNEL_ID) ?: return true
    return rows(CHANNEL, owner, channel).isNotEmpty()
}

/** The calling app's package, or its uid where the system names none. */
private fun ContentProvider.caller(): String = callingPackage ?: "uid:${Binder.getCallingUid()}"

private fun kindUri(kind: String): Uri = Uri.parse("content://$AUTHORITY/$kind")

private fun itemId(uri: Uri): Long? = uri.pathSegments.getOrNull(1)?.toLongOrNull()

private const val ID = "_id"
private const val PACKAGE_NAME = "package_name"

private fun rowValue(row: RowStore.Row, column: String): Any? = when (column) {
    ID -> row.id
    PACKAGE_NAME -> row.packageName
    else -> row.values[column]
}

/** `preview_program?channel=<id>` and the like narrow the rows to one channel. */
private fun List<RowStore.Row>.inChannelOf(uri: Uri): List<RowStore.Row> {
    val channel = uri.getQueryParameter("channel") ?: return this
    return filter { it.values[CHANNEL_ID]?.toString() == channel }
}
