package com.secondbrain.app.data.database

import androidx.room.*
import com.secondbrain.app.data.model.GroupEntity
import com.secondbrain.app.data.model.NoteEntity
import com.secondbrain.app.data.model.NoteGroupCrossRef
import kotlinx.coroutines.flow.Flow

/** Grup + jumlah catatan aktif di dalamnya (untuk layar daftar Grup). */
data class GroupWithCount(
    @Embedded val group: GroupEntity,
    val noteCount: Int
)

@Dao
interface GroupDao {

    // ---- Grup ----

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    /** Cari nama persis (COLLATE NOCASE di kolom) — termasuk grup terarsip,
     *  karena unique index juga mencakup baris arsip. */
    @Query("SELECT * FROM groups WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun activeGroups(): Flow<List<GroupEntity>>

    /** Nama grup aktif untuk disuntikkan ke prompt (terbaru dulu). */
    @Query("SELECT name FROM groups WHERE isArchived = 0 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun activeGroupNames(limit: Int): List<String>

    @Query("""
        SELECT groups.*,
               (SELECT COUNT(*) FROM note_group_cross_ref c
                INNER JOIN notes n ON n.id = c.noteId AND n.isArchived = 0
                WHERE c.groupId = groups.id) AS noteCount
        FROM groups WHERE isArchived = 0 ORDER BY createdAt DESC
    """)
    fun activeGroupsWithCount(): Flow<List<GroupWithCount>>

    // ---- Keanggotaan ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCrossRef(ref: NoteGroupCrossRef)

    @Query("DELETE FROM note_group_cross_ref WHERE noteId = :noteId AND groupId = :groupId")
    suspend fun removeCrossRef(noteId: Long, groupId: Long)

    @Query("""
        SELECT notes.* FROM notes
        INNER JOIN note_group_cross_ref c ON notes.id = c.noteId
        WHERE c.groupId = :groupId AND notes.isArchived = 0
        ORDER BY notes.createdAt DESC
    """)
    fun notesInGroup(groupId: Long): Flow<List<NoteEntity>>

    @Query("""
        SELECT groups.* FROM groups
        INNER JOIN note_group_cross_ref c ON groups.id = c.groupId
        WHERE c.noteId = :noteId AND groups.isArchived = 0
        ORDER BY groups.name
    """)
    fun groupsOfNote(noteId: Long): Flow<List<GroupEntity>>

    /** Jumlah keanggotaan (untuk dialog konfirmasi hapus grup). */
    @Query("SELECT COUNT(*) FROM note_group_cross_ref WHERE groupId = :groupId")
    suspend fun memberCount(groupId: Long): Int
}
