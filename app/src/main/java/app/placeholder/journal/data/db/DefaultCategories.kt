package app.placeholder.journal.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

/** Seeded once when the database is first created. Users will be able to edit them in Settings later. */
object DefaultCategories {
    val names = listOf("커리어", "성장", "개발", "사이드 프로젝트", "일상", "관계", "취미")

    fun seed(db: SupportSQLiteDatabase, now: Long = System.currentTimeMillis()) {
        names.forEachIndexed { index, name ->
            db.execSQL(
                "INSERT INTO categories (id, name, sort_order, created_at) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(UUID.randomUUID().toString(), name, index, now),
            )
        }
    }
}
