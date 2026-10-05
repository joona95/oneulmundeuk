package app.placeholder.journal.data.db

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
