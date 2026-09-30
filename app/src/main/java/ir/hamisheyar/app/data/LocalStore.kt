package ir.hamisheyar.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ChatMessage(
    val id: Long,
    val role: String,
    val text: String,
    val createdAt: Long
)

data class InboxEvent(
    val id: Long,
    val source: String,
    val sender: String,
    val body: String,
    val packageName: String,
    val notificationKey: String?,
    val kind: String,
    val sharedUri: String?,
    val createdAt: Long
)

class LocalStore(context: Context) {
    private val helper = db(context.applicationContext)

    fun addMessage(role: String, text: String): Long {
        val values = ContentValues().apply {
            put("role", role)
            put("text", text)
            put("created_at", System.currentTimeMillis())
        }
        return helper.writableDatabase.insert("messages", null, values)
    }

    fun messages(limit: Int = 200): List<ChatMessage> {
        val out = mutableListOf<ChatMessage>()
        helper.readableDatabase.query(
            "messages",
            arrayOf("id", "role", "text", "created_at"),
            null, null, null, null,
            "created_at DESC",
            limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                out += ChatMessage(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
            }
        }
        return out.reversed()
    }

    fun addEvent(
        source: String,
        sender: String,
        body: String,
        packageName: String,
        notificationKey: String? = null,
        kind: String = "notification",
        sharedUri: String? = null
    ): Long {
        val values = ContentValues().apply {
            put("source", source)
            put("sender", sender)
            put("body", body)
            put("package_name", packageName)
            put("notification_key", notificationKey)
            put("kind", kind)
            put("shared_uri", sharedUri)
            put("created_at", System.currentTimeMillis())
        }
        return helper.writableDatabase.insert("events", null, values)
    }

    fun events(limit: Int = 120): List<InboxEvent> {
        val out = mutableListOf<InboxEvent>()
        helper.readableDatabase.query(
            "events",
            arrayOf("id", "source", "sender", "body", "package_name", "notification_key", "kind", "shared_uri", "created_at"),
            null, null, null, null,
            "created_at DESC",
            limit.toString()
        ).use { c ->
            while (c.moveToNext()) {
                out += InboxEvent(
                    id = c.getLong(0),
                    source = c.getString(1),
                    sender = c.getString(2),
                    body = c.getString(3),
                    packageName = c.getString(4),
                    notificationKey = c.getString(5),
                    kind = c.getString(6),
                    sharedUri = c.getString(7),
                    createdAt = c.getLong(8)
                )
            }
        }
        return out
    }

    fun latestEvent(): InboxEvent? = events(1).firstOrNull()

    fun clearAll() {
        helper.writableDatabase.delete("messages", null, null)
        helper.writableDatabase.delete("events", null, null)
    }

    private class Db(context: Context) : SQLiteOpenHelper(context, "hamisheyar.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE messages(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    role TEXT NOT NULL,
                    text TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )"""
            )
            db.execSQL(
                """CREATE TABLE events(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    source TEXT NOT NULL,
                    sender TEXT NOT NULL,
                    body TEXT NOT NULL,
                    package_name TEXT NOT NULL,
                    notification_key TEXT,
                    kind TEXT NOT NULL,
                    shared_uri TEXT,
                    created_at INTEGER NOT NULL
                )"""
            )
            db.execSQL("CREATE INDEX idx_events_created_at ON events(created_at DESC)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        @Volatile private var INSTANCE: Db? = null

        private fun db(context: Context): Db =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Db(context).also { INSTANCE = it }
            }
    }
}
