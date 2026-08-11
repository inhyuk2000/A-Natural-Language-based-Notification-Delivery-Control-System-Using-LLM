package com.example.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHelper(
    context: Context?,
    name: String? = DB_NAME,
    factory: SQLiteDatabase.CursorFactory? = null,
    version: Int = DB_VERSION,
) : SQLiteOpenHelper(context, name, factory, version) {

    companion object {
        const val DB_NAME = "llm_db.db"
        const val DB_VERSION = 6
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ContextManager (
                id INTEGER PRIMARY KEY,
                name TEXT,
                content TEXT,
                exceptions TEXT,
                delivery TEXT,
                activity TEXT,
                location TEXT,
                expires TEXT,
                mode TEXT DEFAULT 'allow',
                status TEXT DEFAULT 'active',
                recurrence TEXT DEFAULT 'none',
                days_of_week TEXT DEFAULT '',
                window_start TEXT DEFAULT '',
                window_end TEXT DEFAULT ''
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Notifications (
                deviceId TEXT,
                package_name TEXT,
                id INTEGER PRIMARY KEY,
                post_time TEXT NOT NULL,
                text TEXT,
                title TEXT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS NotifLog (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT,
                title TEXT,
                text TEXT,
                post_time TEXT,
                status TEXT,
                created_at INTEGER,
                hidden INTEGER DEFAULT 0
            );
            """.trimIndent()
        )

        createUserDataTable(db)
    }

    private fun createUserDataTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS UserData (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id TEXT NOT NULL UNIQUE,
                nickname TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE ContextManager ADD COLUMN mode TEXT DEFAULT 'allow'")
            } catch (_: Exception) {
                // column may already exist
            }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS NotifLog (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    package_name TEXT,
                    title TEXT,
                    text TEXT,
                    post_time TEXT,
                    status TEXT,
                    created_at INTEGER,
                    hidden INTEGER DEFAULT 0
                );
                """.trimIndent()
            )
        }
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE ContextManager ADD COLUMN status TEXT DEFAULT 'active'")
            } catch (_: Exception) {
                // column may already exist
            }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE NotifLog ADD COLUMN hidden INTEGER DEFAULT 0")
            } catch (_: Exception) {
                // column may already exist
            }
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE ContextManager ADD COLUMN recurrence TEXT DEFAULT 'none'")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("ALTER TABLE ContextManager ADD COLUMN days_of_week TEXT DEFAULT ''")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("ALTER TABLE ContextManager ADD COLUMN window_start TEXT DEFAULT ''")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("ALTER TABLE ContextManager ADD COLUMN window_end TEXT DEFAULT ''")
            } catch (_: Exception) {
            }
        }
        if (oldVersion < 6) {
            createUserDataTable(db)
        }
    }
}
