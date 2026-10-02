package app.placeholder.journal.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import app.placeholder.journal.data.db.RecordEntity

/** A record joined with its category name (null = 미분류). Used directly by the UI. */
data class RecordWithCategory(
    @Embedded val record: RecordEntity,
    @ColumnInfo(name = "category_name") val categoryName: String?,
)
