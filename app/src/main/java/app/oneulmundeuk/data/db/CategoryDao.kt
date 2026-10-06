package app.oneulmundeuk.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sort_order, created_at")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity)

    /** "삭제" = archive: the row stays (records keep it); already archived → unchanged. Never a real DELETE. */
    @Query("UPDATE categories SET archived_at = :at WHERE id = :id AND archived_at IS NULL")
    suspend fun archive(id: String, at: Long)
}
