package app.oneulmundeuk.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local-first store.
 * v1 = records + categories. v2 (M6-2, [MIGRATION_1_2]) = + related-record tables (record_embedding,
 * related_analysis, related_judgment), all CASCADE-deleted with their record.
 * v3 ([MIGRATION_2_3]) = categories.archived_at (category "삭제" is an archive, never a DELETE).
 * v4 ([MIGRATION_3_4], M6-9) = record_embedding keyed by (record_id, model_id): one embedding per embedding space.
 * No destructive migration.
 *
 * TODO(data-protection milestone, before release): personal records are stored unencrypted for now.
 *  Evaluate SQLCipher (or equivalent) with a Keystore-held key, encrypted export/backup, and app lock.
 */
@Database(
    entities = [
        RecordEntity::class,
        CategoryEntity::class,
        RecordEmbeddingEntity::class,
        RelatedAnalysisEntity::class,
        RelatedJudgmentEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(EmotionConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun relatedDao(): RelatedDao

    companion object {
        private const val NAME = "journal.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(SEED_DEFAULT_CATEGORIES)
                .addCallback(ENFORCE_FOREIGN_KEYS)
                .build()

        /** Fresh install only (Room calls onCreate once, never on upgrade). Tests reuse it. */
        val SEED_DEFAULT_CATEGORIES: Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                DefaultCategories.seed(db)
            }
        }

        /**
         * SQLite enforces foreign keys only when asked, per connection. Needed so deleting a record cascades into the
         * related tables (categories are archived, not deleted, so records keep their category_id).
         * Tests building their own (in-memory) database add the same callback.
         */
        val ENFORCE_FOREIGN_KEYS: Callback = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys = ON")
            }
        }
    }
}
