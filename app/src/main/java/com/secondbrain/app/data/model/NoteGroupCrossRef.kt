package com.secondbrain.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Keanggotaan catatan di grup (many-to-many). Hapus catatan/grup → baris ikut terhapus. */
@Entity(
    tableName = "note_group_cross_ref",
    primaryKeys = ["noteId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class NoteGroupCrossRef(
    val noteId: Long,
    val groupId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
