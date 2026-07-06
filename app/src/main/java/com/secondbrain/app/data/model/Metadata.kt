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
    val preparationTime: String? = null,
    // Field di bawah nullable (bukan emptyList) karena metadataJson catatan LAMA tidak
    // memuatnya dan Gson mengabaikan default Kotlin — baca selalu lewat .orEmpty().
    /** Transaksi keuangan hasil prompt Keuangan. */
    val transactions: List<Transaction>? = null,
    /** Kegiatan terjadwal ke-2 dst. dalam satu catatan (kegiatan #1 mengisi field jadwal utama). */
    val extraSchedules: List<ExtraSchedule>? = null,
    /** Rekomendasi AI: pengingat catatan ini sebaiknya alarm keras (pra-centang di Preview). */
    val suggestAlarm: Boolean = false
)

/** Satu transaksi keuangan. Field string nullable karena diisi Gson dari output AI. */
data class Transaction(
    val type: String? = null,           // expense|income
    val item: String? = null,
    val category: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    val amount: Double = 0.0,
    val currency: String? = null,       // default tampilan IDR
    val paymentMethod: String? = null,
    val merchant: String? = null,
    val person: String? = null,
    val date: String? = null,           // YYYY-MM-DD
    val notes: String? = null
) {
    /** Baris ringkas untuk Preview/Detail, mis. "• kopi ×2: -Rp30.000 (Minuman)". */
    fun displayLine(): String {
        val qty = quantity?.takeIf { it > 0 && it != 1.0 }?.let { q ->
            " ×" + (if (q % 1.0 == 0.0) q.toInt().toString() else q.toString()) + (unit?.let { " $it" } ?: "")
        } ?: ""
        val sign = if (type.equals("income", ignoreCase = true)) "+" else "-"
        val cur = if (currency == null || currency.equals("IDR", true)) "Rp" else "$currency "
        val amt = java.text.NumberFormat.getNumberInstance(java.util.Locale("in", "ID")).format(amount)
        val name = item?.takeIf { it.isNotBlank() } ?: category?.takeIf { it.isNotBlank() } ?: "transaksi"
        val cat = category?.takeIf { it.isNotBlank() && it != name }?.let { " ($it)" } ?: ""
        return "• $name$qty: $sign$cur$amt$cat"
    }
}

/** Kegiatan terjadwal tambahan (ke-2 dst.) — tetap dibuatkan pengingat/alarm. */
data class ExtraSchedule(
    val type: String? = null,           // meeting|task|event|reminder
    val title: String? = null,
    val dates: List<String>? = null,    // YYYY-MM-DD
    val startTime: String? = null,      // HH:mm
    val useAlarm: Boolean = false
) {
    fun displayLine(): String = buildString {
        append("• ").append(title?.takeIf { it.isNotBlank() } ?: "(tanpa judul)")
        dates.orEmpty().firstOrNull()?.let { append(" — ").append(it) }
        startTime?.let { append(" ").append(it) }
        if ((dates?.size ?: 0) > 1) append(" (+${dates!!.size - 1} tanggal)")
        if (useAlarm) append(" ⏰")
    }
}

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
    val deadline: String? = null,
    /** Dicentang user (kanban/daftar aksi). Boolean primitif → catatan lama aman (false). */
    val done: Boolean = false
)

/** Transaksi beserta acuan ke catatan induknya (untuk halaman Keuangan).
 *  [date] = tanggal transaksi, fallback tanggal catatan dibuat bila AI tidak mengisi. */
data class TransactionRef(
    val noteId: Long,
    val noteTitle: String,
    val tx: Transaction,
    val date: java.time.LocalDate
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
