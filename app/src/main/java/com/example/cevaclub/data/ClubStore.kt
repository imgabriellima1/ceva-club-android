package com.example.cevaclub.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class ClubStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "ceva_club.db", null, 1) {

    data class Member(
        val id: Long,
        val name: String,
        val email: String,
        val beerProfile: BeerProfile?,
        val plan: Plan?,
        val active: Boolean,
    )

    private val session = context.getSharedPreferences("ceva_session", Context.MODE_PRIVATE)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE members (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                salt BLOB NOT NULL,
                password_hash BLOB NOT NULL,
                password_algorithm TEXT NOT NULL,
                password_rounds INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE quiz_results (
                member_id INTEGER PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
                answers TEXT NOT NULL,
                profile TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE subscriptions (
                member_id INTEGER PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
                plan TEXT NOT NULL,
                active INTEGER NOT NULL CHECK (active IN (0, 1))
            )
            """.trimIndent()
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Migração SQLite ausente: $oldVersion → $newVersion")
    }

    fun currentMember(): Member? {
        val id = session.getLong(SESSION_MEMBER, -1)
        if (id == -1L) return null
        return readableDatabase.rawQuery(
            """
            SELECT m.id, m.name, m.email, q.profile, s.plan, s.active
            FROM members m
            LEFT JOIN quiz_results q ON q.member_id = m.id
            LEFT JOIN subscriptions s ON s.member_id = m.id
            WHERE m.id = ?
            """.trimIndent(),
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else Member(
                cursor.getLong(0), cursor.getString(1), cursor.getString(2),
                BeerProfile.fromCode(cursor.getString(3)), Plan.fromCode(cursor.getString(4)),
                !cursor.isNull(5) && cursor.getInt(5) == 1,
            )
        }
    }

    fun register(name: String, email: String, password: String): Boolean {
        val credential = PasswordHash.create(password)
        val values = ContentValues().apply {
            put("name", name)
            put("email", email)
            put("salt", credential.salt)
            put("password_hash", credential.hash)
            put("password_algorithm", credential.algorithm)
            put("password_rounds", credential.rounds)
        }
        return writableDatabase.insertWithOnConflict(
            "members", null, values, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L
    }

    fun login(email: String, password: String): Member? {
        val record = readableDatabase.query(
            "members", arrayOf("id", "salt", "password_hash", "password_algorithm", "password_rounds"),
            "email = ?", arrayOf(email), null, null, null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getLong(0) to PasswordHash.Credential(
                cursor.getBlob(1), cursor.getBlob(2), cursor.getString(3), cursor.getInt(4)
            )
        } ?: return null
        val (id, credential) = record
        if (!PasswordHash.matches(password, credential)) return null
        check(session.edit().putLong(SESSION_MEMBER, id).commit())
        return currentMember()
    }

    fun logout() {
        check(session.edit().remove(SESSION_MEMBER).commit())
    }

    fun saveQuiz(answers: List<Int>): Member {
        val profile = BeerQuiz.classify(answers)
        save("quiz_results", ContentValues().apply {
            put("answers", answers.joinToString(","))
            put("profile", profile.code)
        })
        return checkNotNull(currentMember())
    }

    fun subscribe(plan: Plan): Member {
        save("subscriptions", ContentValues().apply {
            put("plan", plan.code)
            put("active", 1)
        })
        return checkNotNull(currentMember())
    }

    fun cancel(): Member {
        writableDatabase.update(
            "subscriptions", ContentValues().apply { put("active", 0) },
            "member_id = ? AND active = ?", arrayOf(currentMemberId().toString(), "1"),
        )
        return checkNotNull(currentMember())
    }

    private fun save(table: String, values: ContentValues) {
        values.put("member_id", currentMemberId())
        writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            .also { check(it != -1L) }
    }

    private fun currentMemberId() = session.getLong(SESSION_MEMBER, -1).also { check(it != -1L) }

    companion object {
        private const val SESSION_MEMBER = "member_id"
        @Volatile private var instance: ClubStore? = null

        fun get(context: Context): ClubStore = instance ?: synchronized(this) {
            instance ?: ClubStore(context.applicationContext).also { instance = it }
        }
    }
}
