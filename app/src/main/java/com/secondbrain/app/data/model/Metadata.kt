package com.secondbrain.app.data.model

data class Metadata(
    val title: String = "",
    val type: NoteType = NoteType.NOTE,
    val startTime: String? = null,
    val endTime: String? = null,
    val locations: List<LocationEntry> = emptyList(),
    val entities: Entities = Entities(),
    val keywords: List<String> = emptyList(),
    val recurrenceDates: List<String> = emptyList(),
    val actions: List<ActionItem> = emptyList(),
    val summary: String = "",
    /** Rekomendasi AI (string mentah, dipetakan ke enum dengan fallback). */
    val priority: String? = null,
    val status: String? = null,
    /** Waktu persiapan absolut "yyyy-MM-ddTHH:mm" — diisi hanya jika user minta diingatkan persiapan. */
    val preparationTime: String? = null
)

enum class NoteType(val label: String) {
    MEETING("Meeting"),
    TASK("Tugas"),
    REMINDER("Pengingat"),
    EVENT("Acara"),
    NOTE("Catatan"),
    IDEA("Ide"),
    PERSONAL("Personal")
}

data class LocationEntry(
    val type: String = "location",
    val value: String = ""
)

data class Entities(
    val people: List<String> = emptyList(),
    val organizations: List<String> = emptyList()
)

data class ActionItem(
    val action: String = "",
    val owner: String? = null,
    val deadline: String? = null
)

/** Action item beserta acuan ke catatan induknya (untuk layar agregasi). */
data class ActionItemRef(
    val noteId: Long,
    val noteTitle: String,
    val action: String,
    val owner: String?,
    val deadline: String?,
    val done: Boolean
)

enum class Priority(val label: String) {
    PENTING_URGEN("Penting & Urgen"),
    PENTING_TIDAK_URGEN("Penting, Tidak Urgen"),
    URGEN_TIDAK_PENTING("Urgen, Tidak Penting"),
    TIDAK_PENTING_TIDAK_URGEN("Tidak Penting & Tidak Urgen");

    companion object {
        fun fromString(s: String?): Priority? =
            s?.trim()?.let { v -> entries.firstOrNull { it.name.equals(v, ignoreCase = true) } }
    }
}

enum class NoteStatus(val label: String) {
    BELUM_MULAI("Belum Mulai"),
    BERJALAN("Berjalan"),
    SELESAI("Selesai");

    companion object {
        fun fromString(s: String?): NoteStatus? =
            s?.trim()?.let { v -> entries.firstOrNull { it.name.equals(v, ignoreCase = true) } }
    }
}

enum class InputSource { VOICE, TEXT }
