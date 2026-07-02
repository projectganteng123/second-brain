package com.secondbrain.app.data.repository

import com.google.gson.Gson
import com.secondbrain.app.data.GsonProvider
import com.secondbrain.app.data.database.NoteDao
import com.secondbrain.app.data.database.ReminderDao
import com.secondbrain.app.data.model.*
import com.secondbrain.app.util.DebugLog
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class NoteRepository(
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao,
    private val gson: Gson = GsonProvider.gson
) {

    fun getAllActive(): Flow<List<NoteEntity>> = noteDao.getAllActive()
    fun getArchived(): Flow<List<NoteEntity>> = noteDao.getArchived()
    fun search(query: String): Flow<List<NoteEntity>> = noteDao.search(query)

    suspend fun save(
        rawText: String,
        metadata: Metadata,
        prioritas: Priority? = null,
        status: NoteStatus? = null,
        source: InputSource = InputSource.TEXT,
        offsetHours: Int = 24,
        useAlarm: Boolean = false,
        attachments: List<Attachment> = emptyList()
    ): Long {
        val metaJson = gson.toJson(metadata)
        val entity = NoteEntity(
            rawText = rawText,
            metadataJson = metaJson,
            prioritas = prioritas?.name,
            status = status?.name,
            source = source.name.lowercase(),
            useAlarm = useAlarm,
            attachmentsJson = Attachment.listToJson(attachments)
        )
        val id = noteDao.insert(entity)
        DebugLog.log("DB ✓ simpan", "id=$id, tanggal=${metadata.recurrenceDates}, jam=${metadata.startTime}, alarm=$useAlarm, lampiran=${attachments.size}\nmetadata=$metaJson")
        generateReminders(id, metadata, offsetHours, useAlarm)
        return id
    }

    suspend fun savePending(
        rawText: String,
        source: InputSource,
        attachments: List<Attachment> = emptyList()
    ): Long {
        val entity = NoteEntity(
            rawText = rawText,
            source = source.name.lowercase(),
            isPendingExtraction = true,
            attachmentsJson = Attachment.listToJson(attachments)
        )
        return noteDao.insert(entity)
    }

    suspend fun updateMetadata(id: Long, metadata: Metadata, offsetHours: Int = 24) {
        val existing = noteDao.getById(id) ?: return
        noteDao.update(existing.copy(
            metadataJson = gson.toJson(metadata),
            isPendingExtraction = false,
            updatedAt = System.currentTimeMillis()
        ))
        reminderDao.deleteByNote(id)
        generateReminders(id, metadata, offsetHours, existing.useAlarm)
    }

    suspend fun setUseAlarm(id: Long, useAlarm: Boolean, offsetHours: Int) {
        val existing = noteDao.getById(id) ?: return
        noteDao.update(existing.copy(useAlarm = useAlarm, updatedAt = System.currentTimeMillis()))
        val meta = metadataFrom(existing) ?: return
        reminderDao.deleteByNote(id)
        generateReminders(id, meta, offsetHours, useAlarm)
    }

    suspend fun update(note: NoteEntity) = noteDao.update(note)
    suspend fun delete(note: NoteEntity) = noteDao.delete(note)
    suspend fun getById(id: Long): NoteEntity? = noteDao.getById(id)
    suspend fun setArchived(id: Long, archived: Boolean) = noteDao.setArchived(id, archived)
    suspend fun setStatus(id: Long, status: NoteStatus?) = noteDao.setStatus(id, status?.name)
    suspend fun setPrioritas(id: Long, p: Priority?) = noteDao.setPrioritas(id, p?.name)
    suspend fun getPending(): List<NoteEntity> = noteDao.getPendingExtraction()

    suspend fun exportJson(): String {
        val notes = noteDao.getAllOnce()
        return gson.toJson(notes)
    }

    /**
     * Impor hasil ekspor JSON (List<NoteEntity>). Catatan dengan id yang sama akan DITIMPA
     * (restore), selebihnya digabung. Reminder dibuat ulang untuk catatan aktif yang
     * jadwalnya masih di masa depan. Mengembalikan jumlah catatan yang diimpor.
     */
    suspend fun importJson(json: String, offsetHours: Int = 24): Int {
        val type = object : com.google.gson.reflect.TypeToken<List<NoteEntity>>() {}.type
        val notes: List<NoteEntity> = gson.fromJson(json, type)
            ?: throw RuntimeException("File tidak berisi data catatan yang valid.")
        var count = 0
        for (n in notes) {
            noteDao.insert(n)
            count++
            if (!n.isArchived) {
                val meta = metadataFrom(n)
                if (meta != null) {
                    reminderDao.deleteByNote(n.id)
                    generateReminders(n.id, meta, offsetHours, n.useAlarm)
                }
            }
        }
        DebugLog.log("DB ✓ impor", "$count catatan diimpor dari JSON")
        return count
    }

    suspend fun exportCsv(): String {
        val notes = noteDao.getAllOnce()
        val sb = StringBuilder()
        fun esc(s: String?): String = "\"" + (s ?: "").replace("\"", "\"\"").replace("\n", " ") + "\""
        sb.append("id,createdAt,updatedAt,title,type,startTime,endTime,recurrenceDates,locations,people,organizations,keywords,actions,preparationTime,priorityAI,statusAI,priorityManual,statusManual,useAlarm,archived,summary,rawText\n")
        for (n in notes) {
            val m = metadataFrom(n)
            val actionsStr = m?.actions?.joinToString("; ") { a ->
                buildString {
                    append(a.action)
                    a.owner?.let { append(" (").append(it).append(")") }
                    a.deadline?.let { append(" -> ").append(it) }
                }
            }
            sb.append(n.id).append(',')
                .append(esc(java.time.Instant.ofEpochMilli(n.createdAt).toString())).append(',')
                .append(esc(java.time.Instant.ofEpochMilli(n.updatedAt).toString())).append(',')
                .append(esc(m?.title)).append(',')
                .append(esc(m?.type?.name)).append(',')
                .append(esc(m?.startTime)).append(',')
                .append(esc(m?.endTime)).append(',')
                .append(esc(m?.recurrenceDates?.joinToString("; "))).append(',')
                .append(esc(m?.locations?.joinToString("; ") { it.value })).append(',')
                .append(esc(m?.entities?.people?.joinToString("; "))).append(',')
                .append(esc(m?.entities?.organizations?.joinToString("; "))).append(',')
                .append(esc(m?.keywords?.joinToString("; "))).append(',')
                .append(esc(actionsStr)).append(',')
                .append(esc(m?.preparationTime)).append(',')
                .append(esc(m?.priority)).append(',')
                .append(esc(m?.status)).append(',')
                .append(esc(n.prioritas)).append(',')
                .append(esc(n.status)).append(',')
                .append(n.useAlarm).append(',')
                .append(n.isArchived).append(',')
                .append(esc(m?.summary)).append(',')
                .append(esc(n.rawText)).append('\n')
        }
        return sb.toString()
    }

    /** Semua action items lintas catatan aktif, untuk layar agregasi. */
    fun allActionItems(allNotes: List<NoteEntity>): List<ActionItemRef> =
        allNotes.flatMap { note ->
            val meta = metadataFrom(note)
            meta?.actions.orEmpty().map { act ->
                ActionItemRef(
                    noteId = note.id,
                    noteTitle = meta?.title?.ifBlank { note.rawText.take(40) } ?: note.rawText.take(40),
                    action = act.action,
                    owner = act.owner,
                    deadline = act.deadline,
                    done = note.status == NoteStatus.SELESAI.name
                )
            }
        }

    fun metadataFrom(note: NoteEntity): Metadata? =
        runCatching { gson.fromJson(note.metadataJson, Metadata::class.java) }.getOrNull()

    fun getNotesForDate(date: LocalDate, allNotes: List<NoteEntity>): List<NoteEntity> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val result = allNotes.filter { note ->
            metadataFrom(note)?.recurrenceDates?.contains(dateStr) == true
        }
        DebugLog.log("DB ⌕ hari ini", "cari tanggal=$dateStr → ${result.size} dari ${allNotes.size} catatan aktif")
        return result
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

        val result = scored.sortedByDescending { it.second }.take(limit).map { it.first }
        DebugLog.log("RAG ⌕ retrieval", "tanya=\"$question\", token=$tokens, rentang=$dateRange → ${result.size} catatan terpilih (skor: ${scored.sortedByDescending { it.second }.take(limit).map { it.second }})")
        return result
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

    private suspend fun generateReminders(noteId: Long, metadata: Metadata, offsetHours: Int, useAlarm: Boolean) {
        val reminders = mutableListOf<ReminderEntity>()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val nowMillis = System.currentTimeMillis()

        for (dateStr in metadata.recurrenceDates) {
            val date = runCatching { LocalDate.parse(dateStr, fmt) }.getOrNull() ?: continue

            val eventTime = metadata.startTime?.let {
                runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
            } ?: LocalTime.of(8, 0)

            val eventDateTime = LocalDateTime.of(date, eventTime)
            val eventMillis = eventDateTime.toEpochMilli()

            // Pengingat X jam sebelum kegiatan (sesuai pengaturan user)
            val offsetMillis = eventMillis - offsetHours * 60L * 60L * 1000L
            if (offsetMillis > nowMillis) {
                reminders.add(ReminderEntity(
                    noteId = noteId,
                    remindAt = offsetMillis,
                    message = "Dalam $offsetHours jam: ${metadata.title}",
                    isAlarm = useAlarm
                ))
            }

            // Pengingat saat kegiatan dimulai
            if (eventMillis > nowMillis) {
                reminders.add(ReminderEntity(
                    noteId = noteId,
                    remindAt = eventMillis,
                    message = metadata.title,
                    isAlarm = useAlarm
                ))
            }
        }

        // Pengingat persiapan (waktu absolut), hanya jika diminta user pada catatan
        metadata.preparationTime?.let { prep ->
            val prepMillis = runCatching {
                LocalDateTime.parse(prep, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")).toEpochMilli()
            }.getOrNull()
            if (prepMillis != null && prepMillis > nowMillis) {
                reminders.add(ReminderEntity(
                    noteId = noteId,
                    remindAt = prepMillis,
                    message = "Persiapan: ${metadata.title}",
                    isAlarm = useAlarm
                ))
            }
        }

        if (reminders.isNotEmpty()) {
            reminderDao.insertAll(reminders)
            DebugLog.log("DB ✓ reminder", "id catatan=$noteId → ${reminders.size} pengingat (offset=$offsetHours jam, alarm=$useAlarm)")
        }
    }

    private fun LocalDateTime.toEpochMilli(): Long =
        this.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
