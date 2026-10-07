package app.oneulmundeuk.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (M6-2): adds the related-record tables. Existing `records` / `categories` are untouched — no data is
 * rewritten and no destructive fallback is configured. SQL mirrors what Room generates for the v2 entities
 * (checked by Room's schema validation on open, see MigrationTest).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `record_embedding` (`record_id` TEXT NOT NULL, `model_id` TEXT NOT NULL, " +
                "`text_hash` TEXT NOT NULL, `dim` INTEGER NOT NULL, `vector` BLOB NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`record_id`), FOREIGN KEY(`record_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `related_analysis` (`record_id` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`pipeline_version` TEXT, `text_hash` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `error` TEXT, " +
                "`queued_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `completed_at` INTEGER, `seen_at` INTEGER, " +
                "PRIMARY KEY(`record_id`), FOREIGN KEY(`record_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_related_analysis_status_queued_at` ON `related_analysis` (`status`, `queued_at`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `related_judgment` (`target_id` TEXT NOT NULL, `candidate_id` TEXT NOT NULL, " +
                "`pipeline_version` TEXT NOT NULL, `target_hash` TEXT NOT NULL, `candidate_hash` TEXT NOT NULL, " +
                "`similarity` REAL NOT NULL, `label` INTEGER, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`target_id`, `candidate_id`), " +
                "FOREIGN KEY(`target_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`candidate_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_related_judgment_candidate_id` ON `related_judgment` (`candidate_id`)")
    }
}

/**
 * v2 → v3 (category archive): adds the nullable `categories.archived_at`. Every existing category stays active
 * (NULL); no category is renamed, removed or remapped and no record is touched — old seeds (커리어, 개발, …) remain.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `categories` ADD COLUMN `archived_at` INTEGER")
    }
}

/**
 * v3 → v4 (M6-9): `record_embedding` keyed by (record_id, model_id) instead of record_id, so the same record can keep
 * an embedding per embedding space (e5 Related "query: " and e5 Explore "passage: " are different vectors). SQLite cannot
 * change a primary key in place: new table → copy every row as is (they stay valid: model_id is part of the old rows
 * too) → drop → rename. Nothing else is touched. SQL mirrors what Room generates for the v4 entity (MigrationTest).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `record_embedding_new` (`record_id` TEXT NOT NULL, `model_id` TEXT NOT NULL, " +
                "`text_hash` TEXT NOT NULL, `dim` INTEGER NOT NULL, `vector` BLOB NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`record_id`, `model_id`), FOREIGN KEY(`record_id`) REFERENCES `records`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "INSERT INTO `record_embedding_new` (`record_id`, `model_id`, `text_hash`, `dim`, `vector`, `updated_at`) " +
                "SELECT `record_id`, `model_id`, `text_hash`, `dim`, `vector`, `updated_at` FROM `record_embedding`",
        )
        db.execSQL("DROP TABLE `record_embedding`")
        db.execSQL("ALTER TABLE `record_embedding_new` RENAME TO `record_embedding`")
    }
}
