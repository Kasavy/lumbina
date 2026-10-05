package dev.lumbina.tv.tvprovider

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Base64
import org.json.JSONObject

/**
 * Schema-less storage: every row is its kind (channel, preview_program, …), the owning
 * package and the inserted values as JSON, so any column an app writes round-trips.
 */
internal class RowStore(context: Context) : SQLiteOpenHelper(context, "rows.db", null, 1) {

    class Row(val id: Long, val packageName: String, val values: Map<String, Any?>)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE rows (_id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT, package TEXT, json TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(kind: String, packageName: String, values: ContentValues): Long {
        val row = ContentValues().apply {
            put("kind", kind)
            put("package", packageName)
            put("json", encode(values).toString())
        }
        return writableDatabase.insert("rows", null, row)
    }

    fun rows(kind: String, packageName: String?, id: Long?): List<Row> {
        val where = buildList {
            add("kind = ?" to kind)
            packageName?.let { add("package = ?" to it) }
            id?.let { add("_id = ?" to it.toString()) }
        }
        return readableDatabase.query(
            "rows",
            arrayOf("_id", "package", "json"),
            where.joinToString(" AND ") { it.first },
            where.map { it.second }.toTypedArray(),
            null,
            null,
            "_id",
        ).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor else null }
                .map { Row(it.getLong(0), it.getString(1), decode(JSONObject(it.getString(2)))) }
                .toList()
        }
    }

    fun update(row: Row, values: ContentValues) {
        val merged = encode(row.values).apply {
            val new = encode(values)
            new.keys().forEach { put(it, new.get(it)) }
        }
        writableDatabase.update(
            "rows",
            ContentValues().apply { put("json", merged.toString()) },
            "_id = ?",
            arrayOf(row.id.toString()),
        )
    }

    fun delete(row: Row) {
        writableDatabase.delete("rows", "_id = ?", arrayOf(row.id.toString()))
    }

    private fun encode(values: ContentValues) = encode(values.keySet().associateWith { values.get(it) })

    private fun encode(values: Map<String, Any?>) = JSONObject().apply {
        values.forEach { (key, value) ->
            put(
                key,
                when (value) {
                    null -> JSONObject.NULL
                    is ByteArray -> BLOB_PREFIX + Base64.encodeToString(value, Base64.NO_WRAP)
                    is Boolean -> if (value) 1 else 0
                    else -> value
                },
            )
        }
    }

    private fun decode(json: JSONObject): Map<String, Any?> = json.keys().asSequence().associateWith { key ->
        when (val value = json.get(key)) {
            JSONObject.NULL -> null
            is String if value.startsWith(BLOB_PREFIX) -> Base64.decode(value.removePrefix(BLOB_PREFIX), Base64.NO_WRAP)
            else -> value
        }
    }

    private companion object {
        const val BLOB_PREFIX = "blob:"
    }
}
