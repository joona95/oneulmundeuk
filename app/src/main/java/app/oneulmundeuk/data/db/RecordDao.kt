package app.oneulmundeuk.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.oneulmundeuk.data.model.RecordWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query(
        """
        SELECT records.*, categories.name AS category_name
        FROM records LEFT JOIN categories ON categories.id = records.category_id
        ORDER BY records.created_at DESC
        """,
    )
    fun observeAll(): Flow<List<RecordWithCategory>>

    @Query(
        """
        SELECT records.*, categories.name AS category_name
        FROM records LEFT JOIN categories ON categories.id = records.category_id
        WHERE records.id = :id
        """,
    )
    fun observe(id: String): Flow<RecordWithCategory?>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun get(id: String): RecordEntity?

    @Insert
    suspend fun insert(record: RecordEntity)

    @Update
    suspend fun update(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: String)
}
