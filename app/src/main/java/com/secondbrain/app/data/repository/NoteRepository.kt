package com.secondbrain.app.data.repository

import com.google.gson.Gson
import com.secondbrain.app.data.database.NoteDao
import com.secondbrain.app.data.database.ReminderDao
import com.secondbrain.app.data.model.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class NoteRepository(
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao,
    private val gson: Gson = Gson()
) {

    fun getAllActive(): Flow<List<NoteEntity>> = noteDao.getAllActive()
    fun getArchived(): Flow<List<NoteEntity>> = noteDao.getArchived()
    fun search(query: String): Flow<List<NoteEntity>> = noteDao.search(query)

    suspend fun save(
        rawText: String,
        metadata: Metadata,
        prioritas: Priority? = null,
        status: NoteStatus? = null,
        source: InputSource = InputSource.TEXT
    ): Long {
        val entity = NoteEntity(
            rawText = rawText,
            metadataJson = gson.toJson(metadata),
            prioritas = prioritas?.name,
            status = status?.name,
            source = source.name.lowercase()
        )
        val id = noteDao.insert(entity)
        generateReminders(id, metadata)
        return id
    }

    suspend fun savePending(rawText: String, source: InputSource): Long {
        val entity = NoteEntity(
            rawText = rawText,
            source = source.name.lowercase(),
            isPendingExtraction = true
        )
        return noteDao.insert(entity)
    }

    suspend fun updateMetadata(id: Long, metadata: Metadata) {
        val existing = noteDao.getById(id) ?: return
        noteDao.update(existing.copy(
            metadataJson = gson.toJson(metadata),
            isPendingExtraction = false,
            updatedAt = System.currentTimeMillis()
        ))
        reminderDao.deleteByNote(id)
        generateReminders(id, metadata)
    }

    suspend fun update(note: NoteEntity) = noteDao.update(note)
    suspend fun delete(note: NoteEntity) = noteDao.delete(note)
    suspend fun getById(id: Long): NoteEntity? = noteDao.getById(id)
    suspend fun setArchived(id: Long, archived: Boolean) = noteDao.setArchived(id, archived)
    suspend fun setStatus(id: Long, status: NoteStatus?) = noteDao.setStatus(id, status?.name)
    suspend fun setPrioritas(id: Long, p: Priority?) = noteDao.setPrioritas(id, p?.name)

    fun metadataFrom(note: NoteEntity): Metadata? =
        runCatching { gson.fromJson(note.metadataJson, Metadata::class.java) }.getOrNull()

    fun getNotesForDate(date: LocalDate, allNotes: List<NoteEntity>): List<NoteEntity> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return allNotes.filter { note ->
            metadataFrom(note)?.recurrenceDates?.contains(dateStr) == true
        }
    }

    fun getNotesForRange(from: LocalDate, to: LocalDate, allNotes: List<NoteEntity>): List<NoteEntity> {
        return allNotes.filter { note ->
            metadataFrom(note)?.recurrenceDates?.any { dateStr ->
                runCatching {
                    val d = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
                    !d.isBefore(from) && !d.isAfter(to)
                }.getOrDefault(false)
            } == true
        }.sortedWith(compareBy {
            metadataFrom(it)?.recurrenceDates?.minOrNull()
        })
    }

    /**
     * Local retrieval for Q&A: skor tiap catatan berdasarkan kecocokan kata kunci/entitas
     * dan relevansi waktu relatif (hari ini, besok, minggu ini). Mengembalikan top [limit]
     * catatan dengan skor > 0.
     */
    fun retrieveRelevant(question: String, allNotes: List<NoteEntity>, limit: Int = 6): List<NoteEntity> {
        val q = question.lowercase()
        val tokens = q.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 3 }.toSet()
        val today = LocalDate.now()

        val dateRange: Pair<LocalDate, LocalDate>? = when {
            q.contains("hari ini")   -> today to today
            q.contains("besok")      -> today.plusDays(1) to today.plusDays(1)
            q.contains("lusa")       -> today.plusDays(2) to today.plusDays(2)
            q.contains("minggu ini") -> today to today.plusDays(6)
            q.contains("minggu depan") -> today.plusDays(7) to today.plusDays(13)
            q.contains("bulan ini")  -> today to today.plusDays(30)
            else -> null
        }

        val scored = allNotes.mapNotNull { note ->
            val meta = metadataFrom(note)
            var score = 0
            val haystack = buildString {
                append(note.rawText.lowercase()); append(' ')
                meta?.let {
                    append(it.title.lowercase()); append(' ')
                    append(it.summary.lowercase()); append(' ')
                    append(it.keywords.joinToString(" ").lowercase()); append(' ')
                    append(it.entities.people.joinToString(" ").lowercase()); append(' ')
                    append(it.entities.organizations.joinToString(" ").lowercase())
                }
            }
            tokens.forEach { if (haystack.contains(it)) score += 2 }

            if (dateRange != null && meta != null) {
                val (from, to) = dateRange
                val matchesDate = meta.recurrenceDates.any { ds ->
                    runCatching {
                        val d = LocalDate.parse(ds, DateTimeFormatter.ISO_LOCAL_DATE)
                        !d.isBefore(from) && !d.isAfter(to)
                    }.getOrDefault(false)
                }
                if (matchesDate) score += 5
            }

            if (score > 0) note to score else null
        }

        return scored.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun contextStringFor(note: NoteEntity): String {
        val meta = metadataFrom(note)
        return buildString {
            meta?.let {
                if (it.title.isNotBlank()) append("Judul: ${it.title}. ")
                if (it.recurrenceDates.isNotEmpty()) append("Tanggal: ${it.recurrenceDates.joinToString(", ")}. ")
                if (it.startTime != null) append("Jam: ${it.startTime}. ")
                if (it.summary.isNotBlank()) append("Ringkasan: ${it.summary}. ")
            }
            append("Catatan: ${note.rawText}")
        }
    }

    private suspend fun generateReminders(noteId: Long, metadata: Metadata) {
        val reminders = mutableListOf<ReminderEntity>()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val now = LocalDate.now()

        for (dateStr in metadata.recurrenceDates) {
            val date = runCatching { LocalDate.parse(dateStr, fmt) }.getOrNull() ?: continue
            if (date.isBefore(now)) continue

            val eventTime = metadata.startTime?.let {
                runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
            } ?: LocalTime.of(8, 0)

            val dayBefore = LocalDateTime.of(date.minusDays(1), LocalTime.of(8, 0))
            reminders.add(ReminderEntity(
                noteId = noteId,
                remindAt = dayBefore.toEpochMilli(),
                message = "Besok: ${metadata.title}"
            ))

            val onTime = LocalDateTime.of(date, eventTime)
            reminders.add(ReminderEntity(
                noteId = noteId,
                remindAt = onTime.toEpochMilli(),
                message = metadata.title
            ))
        }

        if (reminders.isNotEmpty()) reminderDao.insertAll(reminders)
    }

    private fun LocalDateTime.toEpochMilli(): Long =
        this.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
