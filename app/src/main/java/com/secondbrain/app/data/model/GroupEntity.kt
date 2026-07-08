package com.secondbrain.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Grup/proyek bernama. Nama unik case-insensitive (COLLATE NOCASE + unique index). */
@Entity(
    tableName = "groups",
    indices = [Index(value = ["name"], unique = true)]
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** Warna chip di UI (hex "#RRGGBB"); null = warna default tema. */
    val color: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Arsip grup menyembunyikannya tanpa menghapus riwayat keanggotaan. */
    val isArchived: Boolean = false
)
