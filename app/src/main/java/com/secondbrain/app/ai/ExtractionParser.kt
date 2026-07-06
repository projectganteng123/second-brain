package com.secondbrain.app.ai

import com.secondbrain.app.data.GsonProvider
import com.secondbrain.app.data.model.ActionItem
import com.secondbrain.app.data.model.Entities
import com.secondbrain.app.data.model.ExtraSchedule
import com.secondbrain.app.data.model.LocationEntry
import com.secondbrain.app.data.model.Metadata
import com.secondbrain.app.data.model.NoteType
import com.secondbrain.app.data.model.Transaction
import com.secondbrain.app.util.DebugLog

/**
 * Parser & penggabung hasil TIGA prompt ekstraksi (Universal, Keuangan, Acara).
 *
 * Semua DTO ber-field nullable: Gson mengisi field yang tidak ada di JSON dengan null
 * (mengabaikan default Kotlin), jadi kebenaran tipe dijamin di sini — Metadata hasil
 * merge() selalu terisi penuh dan aman dibaca downstream.
 */
object ExtractionParser {

    // ---- DTO mentah per prompt ----

    private data class UniversalDto(
        val title: String? = null,
        val type: String? = null,
        val summary: String? = null,
        val keywords: List<String>? = null,
        val locations: List<LocationDto>? = null,
        val entities: EntitiesDto? = null,
        val actions: List<ActionDto>? = null,
        val priority: String? = null,
        val status: String? = null
    )

    private data class LocationDto(val type: String? = null, val value: String? = null)
    private data class EntitiesDto(val people: List<String>? = null, val organizations: List<String>? = null)
    private data class ActionDto(val action: String? = null, val owner: String? = null, val deadline: String? = null)

    private data class FinanceDto(val transactions: List<Transaction>? = null)

    private data class SchedulesDto(val schedules: List<ScheduleDto>? = null)
    private data class ScheduleDto(
        val type: String? = null,
        val title: String? = null,
        val dates: List<String>? = null,
        val startTime: String? = null,
        val endTime: String? = null,
        val location: String? = null,
        val platform: String? = null,
        val participants: List<String>? = null,
        val priority: String? = null,
        val status: String? = null,
        val preparationTime: String? = null,          // legacy / model lama
        val preparationTimes: List<String>? = null,
        val useAlarm: Boolean = false
    )

    // ---- Parsing ----

    /** Bersihkan pagar ```json / teks di sekitar blok { ... }. */
    private fun cleanJson(raw: String): String {
        var cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1)
        return cleaned
    }

    private fun <T> parseAs(raw: String, clazz: Class<T>, label: String): T {
        val cleaned = cleanJson(raw)
        return try {
            GsonProvider.gson.fromJson(cleaned, clazz) ?: throw RuntimeException("JSON kosong")
        } catch (e: Exception) {
            DebugLog.log("AI ✕ parse $label gagal", "${e.message}\n--- JSON mentah ---\n$cleaned")
            throw RuntimeException(
                "AI mengembalikan format $label yang tidak lengkap/terpotong. Coba proses ulang, " +
                "atau persingkat catatan. (lihat panel Debug untuk detail)"
            )
        }
    }

    // ---- Gabung menjadi satu Metadata ----

    fun merge(universalRaw: String, financeRaw: String, scheduleRaw: String): Metadata {
        val u = parseAs(universalRaw, UniversalDto::class.java, "Universal")
        val transactions = parseAs(financeRaw, FinanceDto::class.java, "Keuangan").transactions.orEmpty()
        val schedules = parseAs(scheduleRaw, SchedulesDto::class.java, "Acara").schedules.orEmpty()

        // Kegiatan #1 = jadwal utama catatan; sisanya jadi extraSchedules (tetap dapat pengingat).
        val main = schedules.firstOrNull()
        val extras = schedules.drop(1).map {
            ExtraSchedule(
                type = it.type,
                title = it.title.orEmpty().ifBlank { it.type.orEmpty() },
                dates = it.dates.orEmpty(),
                startTime = it.startTime,
                useAlarm = it.useAlarm
            )
        }

        val locations = (
            u.locations.orEmpty().mapNotNull { l ->
                l.value?.takeIf { it.isNotBlank() }?.let { LocationEntry(l.type ?: "location", it) }
            } + listOfNotNull(
                main?.location?.takeIf { it.isNotBlank() }?.let { LocationEntry("location", it) },
                main?.platform?.takeIf { it.isNotBlank() }?.let { LocationEntry("platform", it) }
            )
        ).distinctBy { it.type to it.value.lowercase() }

        val people = (u.entities?.people.orEmpty() + main?.participants.orEmpty())
            .map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }

        return Metadata(
            title = u.title.orEmpty().ifBlank { main?.title.orEmpty() },
            // Ada jadwal -> jenis dari kegiatan #1 (lebih spesifik); tidak ada -> dari Universal.
            type = noteType(main?.type) ?: noteType(u.type) ?: NoteType.NOTE,
            startTime = main?.startTime,
            endTime = main?.endTime,
            locations = locations,
            entities = Entities(
                people = people,
                organizations = u.entities?.organizations.orEmpty().filter { it.isNotBlank() }
            ),
            keywords = u.keywords.orEmpty().filter { it.isNotBlank() },
            recurrenceDates = main?.dates.orEmpty().filter { it.isNotBlank() },
            actions = u.actions.orEmpty().mapNotNull { a ->
                a.action?.takeIf { it.isNotBlank() }?.let { ActionItem(it, a.owner, a.deadline) }
            },
            summary = u.summary.orEmpty(),
            priority = main?.priority ?: u.priority,
            status = main?.status ?: u.status,
            preparationTimes = (main?.preparationTimes
                ?: main?.preparationTime?.let { listOf(it) }).orEmpty()
                .filter { it.isNotBlank() },
            transactions = transactions.takeIf { it.isNotEmpty() },
            extraSchedules = extras.takeIf { it.isNotEmpty() },
            suggestAlarm = main?.useAlarm == true
        )
    }

    private fun noteType(s: String?): NoteType? =
        s?.trim()?.let { v -> NoteType.entries.firstOrNull { it.name.equals(v, ignoreCase = true) } }
}
