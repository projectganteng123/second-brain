package com.secondbrain.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawText: String,
    val metadataJson: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val prioritas: String? = null,
    val status: String? = null,
    val isArchived: Boolean = false,
    val source: String = "text",
    val isPendingExtraction: Boolean = false,
    /** User mengaktifkan pengingat sebagai alarm (suara + full-screen), bukan sekadar notifikasi. */
    val useAlarm: Boolean = false,
    /** JSON List<Attachment>; kosong = tidak ada lampiran. */
    val attachmentsJson: String = ""
)
