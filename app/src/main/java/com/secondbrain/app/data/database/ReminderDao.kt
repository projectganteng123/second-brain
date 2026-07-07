package com.secondbrain.app.data.database

import androidx.room.*
import com.secondbrain.app.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Query("SELECT * FROM reminders WHERE noteId = :noteId ORDER BY remindAt ASC")
    fun getByNote(noteId: Long): Flow<List<ReminderEntity>>

    @Query("""
        SELECT * FROM reminders
        WHERE isSent = 0
        AND remindAt BETWEEN :from AND :to
        ORDER BY remindAt ASC
    """)
    suspend fun getUpcoming(from: Long, to: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("UPDATE reminders SET isSent = 1 WHERE id = :id")
    suspend fun markSent(id: Long)

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    suspend fun deleteByNote(noteId: Long)
}
