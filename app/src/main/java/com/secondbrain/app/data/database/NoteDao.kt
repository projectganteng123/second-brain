package com.secondbrain.app.data.database

import androidx.room.*
import com.secondbrain.app.data.model.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun getAllActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun getArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("""
        SELECT * FROM notes
        WHERE isArchived = 0
        AND (rawText LIKE '%' || :query || '%'
             OR metadataJson LIKE '%' || :query || '%')
        ORDER BY createdAt DESC
    """)
    fun search(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isPendingExtraction = 1")
    suspend fun getPendingExtraction(): List<NoteEntity>

    @Query("UPDATE notes SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String?, now: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET prioritas = :prioritas, updatedAt = :now WHERE id = :id")
    suspend fun setPrioritas(id: Long, prioritas: String?, now: Long = System.currentTimeMillis())
}
