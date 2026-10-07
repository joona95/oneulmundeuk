package app.oneulmundeuk

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.oneulmundeuk.data.db.AnalysisStatus
import app.oneulmundeuk.data.db.AppDatabase
import app.oneulmundeuk.data.db.MIGRATION_1_2
import app.oneulmundeuk.data.db.MIGRATION_2_3
import app.oneulmundeuk.data.db.MIGRATION_3_4
import app.oneulmundeuk.data.db.RecordEmbeddingEntity
import app.oneulmundeuk.data.db.RelatedAnalysisEntity
import app.oneulmundeuk.data.model.Emotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migrations 1 → 2 → 3 → 4 on a real v1 database file (and 3 → 4 on a v3 file holding an embedding). The v1 schema below is copied from
 * app/schemas/app.oneulmundeuk.data.db.AppDatabase/1.json and written with platform SQLite (no room-testing dependency). Opening with Room
 * runs the migrations and Room's own schema validation against the current (v4) entities — a mismatch throws.
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

    private fun openLatest() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4) // no fallbackToDestructiveMigration: a broken migration fails loudly
        .addCallback(AppDatabase.ENFORCE_FOREIGN_KEYS)
        .allowMainThreadQueries()
        .build()

    @Test
    fun migratesV1KeepingRecordsAndAddsEmptyRelatedTables() = runBlocking {
        createV1()
        val db = openLatest()
        try {
            val sql = db.openHelper.writableDatabase // runs migrate + Room schema validation
            assertEquals(4, sql.version)
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

    /** v3 (category archive): old categories are kept as they were — active, same name, no new seed, no remap. */
    @Test
    fun migrationKeepsExistingCategoriesActiveAndRecordsLinked() = runBlocking {
        createV1()
        val db = openLatest()
        try {
            val categories = db.categoryDao().observeAll().first()
            assertEquals(listOf("c1" to "커리어"), categories.map { it.id to it.name }) // no 회사/일상/… added on upgrade
            assertNull(categories.single().archivedAt)
            assertEquals("c1", db.recordDao().get("r1")?.categoryId)
        } finally {
            db.close()
        }
    }

    @Test
    fun freshDatabaseOpensAtV4() = runBlocking {
        val db = openLatest()
        try {
            assertEquals(4, db.openHelper.writableDatabase.version)
            assertNull(db.relatedDao().nextPending())
        } finally {
            db.close()
        }
    }

    /** A v3 database (as M6-2 … M6-8 left it) with one embedding row. */
    private fun createV3WithEmbedding() {
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            V3_SQL.forEach(db::execSQL)
            db.execSQL("INSERT INTO records VALUES ('r1', '첫 기록', 10, 10, NULL, NULL, NULL)")
            db.execSQL("INSERT INTO record_embedding VALUES ('r1', 'debug-fake-bigram64', 't1:abc', 2, x'0000803F00000000', 7)")
            db.version = 3
        }
    }

    /** v4: record_embedding is keyed by (record_id, model_id) — old rows kept as is, a second space can be added. */
    @Test
    fun migratesV3EmbeddingsToPerSpaceKey() = runBlocking {
        createV3WithEmbedding()
        val db = openLatest()
        try {
            assertEquals(4, db.openHelper.writableDatabase.version) // migrate + Room schema validation
            val dao = db.relatedDao()
            val old = dao.embedding("r1", "debug-fake-bigram64")!!
            assertEquals("t1:abc", old.textHash)
            assertEquals(2, old.dim)
            assertEquals(7L, old.updatedAt)
            dao.upsertEmbedding(RecordEmbeddingEntity("r1", "e5|related-q-q", "t1:abc", 1, ByteArray(4), 8))
            assertNotNull(dao.embedding("r1", "debug-fake-bigram64")) // other space untouched
            assertNotNull(dao.embedding("r1", "e5|related-q-q"))
            db.recordDao().delete("r1") // CASCADE still works on the rebuilt table
            assertNull(dao.embedding("r1", "debug-fake-bigram64"))
            assertNull(dao.embedding("r1", "e5|related-q-q"))
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

        /** app/schemas/…/3.json */
        val V3_SQL = listOf(
            "CREATE TABLE IF NOT EXISTS `records` (`id` TEXT NOT NULL, `text` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `emotion` TEXT, `category_id` TEXT, `photo_path` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_records_created_at` ON `records` (`created_at`)",
            "CREATE INDEX IF NOT EXISTS `index_records_category_id` ON `records` (`category_id`)",
            "CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `archived_at` INTEGER, PRIMARY KEY(`id`))",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)",
            "CREATE TABLE IF NOT EXISTS `record_embedding` (`record_id` TEXT NOT NULL, `model_id` TEXT NOT NULL, `text_hash` TEXT NOT NULL, `dim` INTEGER NOT NULL, `vector` BLOB NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`record_id`), FOREIGN KEY(`record_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `related_analysis` (`record_id` TEXT NOT NULL, `status` TEXT NOT NULL, `pipeline_version` TEXT, `text_hash` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `error` TEXT, `queued_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `completed_at` INTEGER, `seen_at` INTEGER, PRIMARY KEY(`record_id`), FOREIGN KEY(`record_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_related_analysis_status_queued_at` ON `related_analysis` (`status`, `queued_at`)",
            "CREATE TABLE IF NOT EXISTS `related_judgment` (`target_id` TEXT NOT NULL, `candidate_id` TEXT NOT NULL, `pipeline_version` TEXT NOT NULL, `target_hash` TEXT NOT NULL, `candidate_hash` TEXT NOT NULL, `similarity` REAL NOT NULL, `label` INTEGER, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`target_id`, `candidate_id`), FOREIGN KEY(`target_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`candidate_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_related_judgment_candidate_id` ON `related_judgment` (`candidate_id`)",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'eed357a20c12db2b59c49a8f30dcf09b')",
        )
    }
}
