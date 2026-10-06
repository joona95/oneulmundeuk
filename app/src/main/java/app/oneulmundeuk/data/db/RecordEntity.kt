package app.oneulmundeuk.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.oneulmundeuk.data.model.Emotion

@Entity(
    tableName = "records",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("created_at"), Index("category_id")]
)
data class RecordEntity(
    /** UUID string: stable across future export/import without id collisions. */
    @PrimaryKey val id: String,
    val text: String,
    /** Epoch millis (UTC). Converted to the device time zone only for display. */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** Stored as a stable key via [EmotionConverter]. */
    val emotion: Emotion?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    /** Reserved for the photo milestone (Photo Picker + copy into app storage). Always null for now. */
    @ColumnInfo(name = "photo_path") val photoPath: String?,
)
