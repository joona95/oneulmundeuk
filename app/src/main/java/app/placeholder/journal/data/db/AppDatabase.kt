package app.placeholder.journal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Local-first store. Version 1 = records + categories only.
 * Future AI data (e.g. embeddings) arrives as a separate table via Migration(1, 2) — not added yet.
 *
 * TODO(data-protection milestone, before release): personal records are stored unencrypted for now.
 *  Evaluate SQLCipher (or equivalent) with a Keystore-held key, encrypted export/backup, and app lock.
 */
@Database(entities = [RecordEntity::class, CategoryEntity::class], version = 1, exportSchema = true)
@TypeConverters(EmotionConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        private const val NAME = "journal.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DefaultCategories.seed(db)
                    }

                    // Enforce FK so deleting a category sets records.category_id to NULL (미분류).
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()
    }
}
