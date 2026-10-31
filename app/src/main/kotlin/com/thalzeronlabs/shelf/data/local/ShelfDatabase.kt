package com.thalzeronlabs.shelf.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.thalzeronlabs.shelf.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val coverUrl: String?,
    val status: ReadingStatus,
    val addedAt: Long,
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: ReadingStatus)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

class Converters {
    @TypeConverter fun fromStatus(status: ReadingStatus): String = status.name
    @TypeConverter fun toStatus(value: String): ReadingStatus = ReadingStatus.valueOf(value)
}

@Database(entities = [BookEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class ShelfDatabase : RoomDatabase() {
    abstract fun books(): BookDao
}
