package com.ghosa.skymusichelper

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class ConfigDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "app_config.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_NAME = "config"
        const val COLUMN_ID = "_id"
        const val COLUMN_KEY = "config_key"
        const val COLUMN_VALUE = "config_value"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = "CREATE TABLE $TABLE_NAME (" +
                "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "$COLUMN_KEY TEXT UNIQUE, " +
                "$COLUMN_VALUE TEXT)"
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun getConfigValue(key: String): String? {
        val db = readableDatabase
        val projection = arrayOf(COLUMN_VALUE)
        val selection = "$COLUMN_KEY = ?"
        val selectionArgs = arrayOf(key)
        val cursor = db.query(
            TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
        )
        var value: String? = null
        if (cursor.moveToFirst()) {
            value = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VALUE))
        }
        cursor.close()
        return value
    }

    fun setConfigValue(key: String, value: String) {
        val db = writableDatabase
        val insertValues = android.content.ContentValues().apply {
            put(COLUMN_KEY, key)
            put(COLUMN_VALUE, value)
        }
        db.replace(TABLE_NAME, null, insertValues)
    }
}