package com.privacy.whatsappdecryptor.core.inventory

import android.database.sqlite.SQLiteDatabase
import java.io.File

class AndroidInventorySql(file: File) : InventorySql {
    private val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    override fun execute(sql: String, args: List<Any?>) {
        // Android requires row-returning pragmas to use rawQuery.
        if (sql.startsWith("PRAGMA")) db.rawQuery(sql, null).use { it.moveToFirst() }
        else db.execSQL(sql, args.toTypedArray())
    }
    override fun <T> query(sql: String, args: List<Any?>, read: (InventoryCursor) -> T): T =
        db.rawQuery(sql, args.map { it?.toString() ?: "" }.toTypedArray()).use { cursor ->
            read(object : InventoryCursor {
                override fun next() = cursor.moveToNext()
                override fun text(column: Int) = cursor.getString(column)
                override fun long(column: Int) = cursor.getLong(column)
            })
        }
    override fun close() = db.close()
}
