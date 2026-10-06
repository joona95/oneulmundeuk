package app.oneulmundeuk

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.MIGRATION_1_2
import app.oneulmundeuk.data.db.RelatedAnalysisEntity
import app.oneulmundeuk.data.model.Emotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration(1, 2) on a real v1 database file. The v1 schema below is copied from
 * app/schemas/app.oneulmundeuk.data.db.AppDatabase/1.json and written with platform SQLite (no room-testing dependency). Opening with Room
 * runs the migration and Room's own schema validation against the v2 entities — a mismatch throws.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @Before
    fun setUp() { context.deleteDatabase(name) }

    @After
    fun tearDown() { context.deleteDatabase(name) }

    /** A v1 database as the M1–M5 app left it (platform SQLite, user_version 1, Room v1 identity hash). */
    private fun createV1() {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V1_SQL.forEach(db::execSQL)
            db.execSQL("INSERT INTO categories (id, name, sort_order, created_at) VALUES ('c1', '커리어', 0, 1)")
            db.execSQL("INSERT INTO records VALUES ('r1', '첫 기록', 10, 10, 'calm', 'c1', NULL)")
            db.execSQL("INSERT INTO records VALUES ('r2', '둘째 기록', 20, 25, NULL, NULL, NULL)")
            db.version = 1
        }
    }

    private fun openV2() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(MIGRATION_1_2) // no fallbackToDestructiveMigration: a broken migration fails loudly
        .addCallback(AppDatabase.ENFORCE_FOREIGN_KEYS)
        .allowMainThreadQueries()
        .build()

    @Test
    fun migratesV1KeepingRecordsAndAddsEmptyRelatedTables() = runBlocking {
        createV1()
        val db = openV2()
        try {
            val sql = db.openHelper.writableDatabase // runs migrate + Room schema validation
            assertEquals(2, sql.version)
            val records = db.recordDao().observeAll().first()
            assertEquals(listOf("r2", "r1"), records.map { it.record.id })
            assertEquals(Emotion.CALM, records[1].record.emotion)
            assertEquals("커리어", records[1].categoryName)
            listOf("record_embedding", "related_analysis", "related_judgment").forEach { table ->
                sql.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            }
            // FK + CASCADE work on the migrated tables
            db.relatedDao().upsertAnalysis(RelatedAnalysisEntity("r1", AnalysisStatus.PENDING, null, "h", 0, null, 1, 1, null, null))
            db.recordDao().delete("r1")
            assertNull(db.relatedDao().analysis("r1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun freshV2DatabaseOpens() = runBlocking {
        val db = openV2()
        try {
            assertEquals(2, db.openHelper.writableDatabase.version)
            assertNull(db.relatedDao().nextPending())
        } finally {
            db.close()
        }
    }

    private companion object {
        val V1_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `records` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `emotion` TEXT, `category_id` TEXT, `photo_path` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_records_created_at` ON `records` (`created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_records_category_id` ON `records` (`category_id`)",
            "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'f9fcd364b6ca26b0f05529906c94a9e9')",
        )
    }
}
