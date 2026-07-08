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
    private val groupDao: com.secondbrain.app.data.database.GroupDao,
    private val gson: Gson = GsonProvider.gson,
    /** Untuk mencabut alarm di AlarmManager LANGSUNG saat pengingat dihapus (nullable untuk tes). */
    private val appContext: android.content.Context? = null
) {

    /** Hapus baris pengingat sebuah catatan DAN cabut pendaftarannya di AlarmManager
     *  seketika — tidak menunggu sweep berikutnya. */
    private suspend fun removeReminders(noteId: Long) {
        val ids = reminderDao.getIdsByNote(noteId)
        reminderDao.deleteByNote(noteId)
        appContext?.let { com.secondbrain.app.notification.AlarmJanitor.cancelIds(it, ids) }
    }

    fun getAllActive(): Flow<List<NoteEntity>> = noteDao.getAllActive()
    fun getArchived(): Flow<List<NoteEntity>> = noteDao.getArchived()
    fun search(query: String): Flow<List<NoteEntity>> = noteDao.search(query)

    // ---- Grup ----

    fun activeGroups(): Flow<List<GroupEntity>> = groupDao.activeGroups()
    fun activeGroupsWithCount(): Flow<List<com.secondbrain.app.data.database.GroupWithCount>> =
        groupDao.activeGroupsWithCount()
    fun notesInGroup(groupId: Long): Flow<List<NoteEntity>> = groupDao.notesInGroup(groupId)
    fun groupsOfNote(noteId: Long): Flow<List<GroupEntity>> = groupDao.groupsOfNote(noteId)
    suspend fun activeGroupNames(limit: Int = 50): List<String> = groupDao.activeGroupNames(limit)
    suspend fun getGroup(id: Long): GroupEntity? = groupDao.getById(id)
    suspend fun groupMemberCount(groupId: Long): Int = groupDao.memberCount(groupId)

    /** Cari grup by nama (case-insensitive + trim). Belum ada → buat; terarsip → hidupkan
     *  lagi (nama unik mencakup baris arsip, jadi tidak boleh dibuat kembar). */
    suspend fun resolveOrCreateGroup(name: String): GroupEntity? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        groupDao.findByName(clean)?.let { existing ->
            if (!existing.isArchived) return existing
            val revived = existing.copy(isArchived = false)
            groupDao.update(revived)
            return revived
        }
        val id = groupDao.insert(GroupEntity(name = clean))
        DebugLog.log("DB ✓ grup baru", "\"$clean\" (id=$id)")
        return groupDao.getById(id)
    }

    /** Resolve tiap nama lalu tulis keanggotaan (duplikat diabaikan oleh IGNORE). */
    suspend fun assignGroups(noteId: Long, names: List<String>) {
        for (n in names) {
            val g = resolveOrCreateGroup(n) ?: continue
            groupDao.addCrossRef(NoteGroupCrossRef(noteId = noteId, groupId = g.id))
        }
    }

    suspend fun addNoteToGroup(noteId: Long, groupId: Long) =
        groupDao.addCrossRef(NoteGroupCrossRef(noteId = noteId, groupId = groupId))

    suspend fun removeNoteFromGroup(noteId: Long, groupId: Long) =
        groupDao.removeCrossRef(noteId, groupId)

    /** Ganti nama grup. false bila nama kosong atau bentrok dengan grup lain. */
    suspend fun renameGroup(id: Long, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isEmpty()) return false
        val bentrok = groupDao.findByName(clean)
        if (bentrok != null && bentrok.id != id) return false
        val g = groupDao.getById(id) ?: return false
        groupDao.update(g.copy(name = clean))
        return true
    }

    suspend fun setGroupArchived(id: Long, archived: Boolean) {
        val g = groupDao.getById(id) ?: return
        groupDao.update(g.copy(isArchived = archived))
    }

    suspend fun deleteGroup(id: Long) = groupDao.delete(id)

    /** Terima/tolak SATU saran grup pada catatan (jalur pending di Detail).
     *  accept=true → tulis keanggotaan; dua-duanya menghapus nama itu dari saran. */
    suspend fun consumeGroupSuggestion(noteId: Long, name: String, accept: Boolean) {
        val note = noteDao.getById(noteId) ?: return
        val meta = metadataFrom(note) ?: return
        if (accept) {
            resolveOrCreateGroup(name)?.let {
                groupDao.addCrossRef(NoteGroupCrossRef(noteId = noteId, groupId = it.id))
            }
        }
        val sisa = meta.suggestedGroups.orEmpty()
            .filterNot { it.trim().equals(name.trim(), ignoreCase = true) }
        noteDao.update(note.copy(
            metadataJson = gson.toJson(meta.copy(suggestedGroups = sisa.takeIf { it.isNotEmpty() })),
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun save(
        rawText: String,
        metadata: Metadata,
        prioritas: Priority? = null,
        status: NoteStatus? = null,
        source: InputSource = InputSource.TEXT,
        alarmOffsetMinutes: Int = 15,
        useAlarm: Boolean = false,
        attachments: List<Attachment> = emptyList(),
        groupNames: List<String> = emptyList()
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
        generateReminders(id, metadata, alarmOffsetMinutes, useAlarm)
        if (groupNames.isNotEmpty()) assignGroups(id, groupNames)
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

    suspend fun updateMetadata(id: Long, metadata: Metadata, alarmOffsetMinutes: Int = 15) {
        val existing = noteDao.getById(id) ?: return
        noteDao.update(existing.copy(
            metadataJson = gson.toJson(metadata),
            isPendingExtraction = false,
            updatedAt = System.currentTimeMillis()
        ))
        removeReminders(id)
        generateReminders(id, metadata, alarmOffsetMinutes, existing.useAlarm)
    }

    suspend fun setUseAlarm(id: Long, useAlarm: Boolean, alarmOffsetMinutes: Int) {
        val existing = noteDao.getById(id) ?: return
        noteDao.update(existing.copy(useAlarm = useAlarm, updatedAt = System.currentTimeMillis()))
        val meta = metadataFrom(existing) ?: return
        removeReminders(id)
        generateReminders(id, meta, alarmOffsetMinutes, useAlarm)
    }

    suspend fun update(note: NoteEntity) = noteDao.update(note)

    suspend fun delete(note: NoteEntity) {
        removeReminders(note.id)   // hapus baris + cabut alarm-nya seketika
        noteDao.delete(note)
    }

    suspend fun getById(id: Long): NoteEntity? = noteDao.getById(id)

    suspend fun setArchived(id: Long, archived: Boolean) {
        // Catatan diarsipkan tidak boleh berbunyi lagi (dibuat ulang saat catatan diedit/dipulihkan+diedit)
        if (archived) removeReminders(id)
        noteDao.setArchived(id, archived)
    }
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
    suspend fun importJson(json: String, alarmOffsetMinutes: Int = 15): Int {
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
                    removeReminders(n.id)
                    generateReminders(n.id, meta, alarmOffsetMinutes, n.useAlarm)
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
        sb.append("id,createdAt,updatedAt,title,type,startTime,endTime,recurrenceDates,locations,people,organizations,keywords,actions,alarmTimes,priorityAI,statusAI,priorityManual,statusManual,useAlarm,archived,summary,rawText\n")
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
                .append(esc(m?.alarmTimesEffective()?.joinToString("; "))).append(',')
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
                    done = act.done || note.status == NoteStatus.SELESAI.name
                )
            }
        }

    /** Tandai satu action item selesai/belum (indeks pada daftar actions di metadata). */
    suspend fun setActionDone(noteId: Long, actionIndex: Int, done: Boolean) {
        val note = noteDao.getById(noteId) ?: return
        val meta = metadataFrom(note) ?: return
        val actions = meta.actions.toMutableList()
        if (actionIndex !in actions.indices) return
        actions[actionIndex] = actions[actionIndex].copy(done = done)
        noteDao.update(note.copy(
            metadataJson = gson.toJson(meta.copy(actions = actions)),
            updatedAt = System.currentTimeMillis()
        ))
    }

    /** Semua transaksi lintas catatan aktif, untuk halaman Keuangan. */
    fun allTransactions(allNotes: List<NoteEntity>): List<TransactionRef> =
        allNotes.flatMap { note ->
            val meta = metadataFrom(note)
            meta?.transactions.orEmpty().map { tx ->
                val date = tx.date?.let {
                    runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                } ?: java.time.Instant.ofEpochMilli(note.createdAt)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                TransactionRef(
                    noteId = note.id,
                    noteTitle = meta?.title?.ifBlank { note.rawText.take(40) } ?: note.rawText.take(40),
                    tx = tx,
                    date = date
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

    private suspend fun generateReminders(noteId: Long, metadata: Metadata, alarmOffsetMinutes: Int, useAlarm: Boolean) {
        val reminders = mutableListOf<ReminderEntity>()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val nowMillis = System.currentTimeMillis()

        // Per kegiatan per tanggal:
        // 1) ALARM X menit sebelum mulai — hanya bila toggle alarm catatan aktif
        // 2) NOTIFIKASI informasi + kata semangat tepat saat mulai — selalu, bukan alarm
        fun addEventReminders(title: String, dates: List<String>, startTime: String?) {
            for (dateStr in dates) {
                val date = runCatching { LocalDate.parse(dateStr, fmt) }.getOrNull() ?: continue

                val eventTime = startTime?.let {
                    runCatching { LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
                } ?: LocalTime.of(8, 0)

                val eventMillis = LocalDateTime.of(date, eventTime).toEpochMilli()

                if (useAlarm) {
                    val alarmAt = eventMillis - alarmOffsetMinutes * 60_000L
                    if (alarmAt > nowMillis) {
                        reminders.add(ReminderEntity(
                            noteId = noteId,
                            remindAt = alarmAt,
                            message = if (alarmOffsetMinutes == 0) "Sekarang: $title"
                                      else "$alarmOffsetMinutes menit lagi: $title",
                            isAlarm = true
                        ))
                    }
                }

                if (eventMillis > nowMillis) {
                    val jam = String.format("%02d.%02d", eventTime.hour, eventTime.minute)
                    reminders.add(ReminderEntity(
                        noteId = noteId,
                        remindAt = eventMillis,
                        message = "$title · $jam — ${MOTIVATION.random()}",
                        isAlarm = false
                    ))
                }
            }
        }

        addEventReminders(metadata.title, metadata.recurrenceDates, metadata.startTime)

        // Kegiatan ke-2 dst. dalam catatan yang sama mendapat perlakuan yang sama
        for (ex in metadata.extraSchedules.orEmpty()) {
            addEventReminders(
                title = ex.title.orEmpty().ifBlank { metadata.title },
                dates = ex.dates.orEmpty(),
                startTime = ex.startTime
            )
        }

        // Waktu alarm/pengingat eksplisit (boleh lebih dari satu) — SELALU alarm
        for (alarmAt in metadata.alarmTimesEffective()) {
            val millis = runCatching {
                LocalDateTime.parse(alarmAt, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")).toEpochMilli()
            }.getOrNull()
            if (millis != null && millis > nowMillis) {
                reminders.add(ReminderEntity(
                    noteId = noteId,
                    remindAt = millis,
                    message = metadata.title,
                    isAlarm = true
                ))
            }
        }

        val deduped = dedupReminders(reminders)
        if (deduped.isNotEmpty()) {
            reminderDao.insertAll(deduped)
            DebugLog.log("DB ✓ reminder", "id catatan=$noteId → ${deduped.size} pengingat (alarm offset=$alarmOffsetMinutes mnt, alarm=$useAlarm, dedup=${reminders.size - deduped.size})")
        }
    }

    /**
     * Aturan dedup dalam SATU catatan:
     * - Dua alarm berjarak < 5 menit → alarm yang lebih AWAL tetap alarm, yang belakangan
     *   otomatis diturunkan menjadi notifikasi biasa.
     * - Waktu persis sama & jenis sama → cukup satu (satu alarm + satu notifikasi boleh
     *   berbagi waktu yang sama).
     */
    private fun dedupReminders(reminders: List<ReminderEntity>): List<ReminderEntity> {
        val out = mutableListOf<ReminderEntity>()
        for (r in reminders.sortedBy { it.remindAt }) {
            var cur = r
            if (cur.isAlarm) {
                val nearbyAlarm = out.lastOrNull {
                    it.isAlarm && (cur.remindAt - it.remindAt) < 5 * 60_000L
                }
                if (nearbyAlarm != null) cur = cur.copy(isAlarm = false)   // turunkan jadi notifikasi
            }
            val duplicate = out.any { it.remindAt == cur.remindAt && it.isAlarm == cur.isAlarm }
            if (!duplicate) out.add(cur)
        }
        return out
    }

    private fun LocalDateTime.toEpochMilli(): Long =
        this.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        /** Kata semangat untuk notifikasi informasi saat acara dimulai. */
        private val MOTIVATION = listOf(
            "Semangat, kamu pasti bisa! 💪",
            "Saatnya bersinar ✨",
            "Selesaikan dengan tenang 🌿",
            "Kamu sudah siap untuk ini 🚀",
            "Satu langkah lagi, gas! 🎯",
            "Fokus sebentar, hasilnya panjang 🌱"
        )
    }
}
