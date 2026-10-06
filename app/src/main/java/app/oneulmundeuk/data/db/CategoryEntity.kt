package app.oneulmundeuk.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * [archivedAt] != null = the user deleted it (v3). The row is never DELETEd: records keep pointing at it and keep
 * showing its name; it only disappears from the choices for new records (see [app.oneulmundeuk.data.CategoryPolicy]).
 */
@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
)

val CategoryEntity.isArchived: Boolean get() = archivedAt != null
