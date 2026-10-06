package app.oneulmundeuk.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/**
 * Seeded once when the database is first created (fresh install only — upgrades never re-seed or remap; an existing
 * install keeps its own categories, e.g. the old 커리어 / 성장 / 개발 set). Users add / delete (archive) their own later.
 */
object DefaultCategories {
    val names = listOf("회사", "일상", "취미", "관계", "기타")

    fun seed(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
        names.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT INTO categories (id, name, sort_order, created_at) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(UUID.randomUUID().toString(), name, index, now),
            )
        }
    }
}
