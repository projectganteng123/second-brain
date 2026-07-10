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

    @Query("SELECT id FROM reminders WHERE isSent = 0")
    suspend fun getAliveIds(): List<Long>

    /** Semua pengingat yang belum terkirim & belum lewat — untuk halaman Alarm (live). */
    @Query("SELECT * FROM reminders WHERE isSent = 0 AND remindAt >= :from ORDER BY remindAt ASC")
    fun getUpcomingFlow(from: Long): Flow<List<ReminderEntity>>

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT id FROM reminders WHERE noteId = :noteId")
    suspend fun getIdsByNote(noteId: Long): List<Long>

    @Query("UPDATE reminders SET isSent = 1 WHERE id = :id")
    suspend fun markSent(id: Long)

    @Query("DELETE FROM reminders WHERE noteId = :noteId")
    suspend fun deleteByNote(noteId: Long)
}
