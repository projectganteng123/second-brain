package com.secondbrain.app.ai

/**
 * Heuristik hemat kuota: memutuskan prompt Keuangan/Acara perlu dijalankan atau tidak,
 * dari teks mentah catatan. SENGAJA murah hati (bias menjalankan) — melewatkan jadwal
 * atau transaksi jauh lebih mahal daripada satu panggilan API ekstra.
 * Prompt Universal selalu jalan.
 */
object ExtractionHeuristics {

    private val FINANCE_WORDS = listOf(
        "rp", "rupiah", "harga", "beli", "membeli", "bayar", "membayar", "jual", "belanja",
        "gaji", "bonus", "transfer", "hutang", "utang", "piutang", "cicilan", "tagihan",
        "uang", "duit", "biaya", "ongkos", "ongkir", "diskon", "kembalian", "top up", "topup",
        "saldo", "invoice", "donasi", "sedekah", "zakat", "pajak", "iuran", "patungan",
        "langganan", "ribu", "juta"
    )

    /** Angka bergaya uang: 25rb, 10k, 1,5jt, Rp5000, atau angka ≥4 digit (harga polos). */
    private val MONEY_PATTERN = Regex("\\d[\\d.,]*\\s*(rb|ribu|jt|juta|k)\\b|rp\\s*\\d|\\d{4,}")

    private val SCHEDULE_WORDS = listOf(
        "jam", "pukul", "besok", "lusa", "nanti", "hari ini", "malam ini",
        "senin", "selasa", "rabu", "kamis", "jumat", "jum'at", "sabtu", "minggu", "ahad",
        "januari", "februari", "maret", "april", "mei", "juni", "juli", "agustus",
        "september", "oktober", "november", "desember",
        "tanggal", "pagi", "siang", "sore", "malam", "subuh",
        "deadline", "jadwal", "ingatkan", "reminder", "alarm", "meeting", "rapat", "acara",
        "event", "janji", "janjian", "ketemu", "kumpul", "berangkat", "mulai", "selesai",
        "setiap", "tiap", "menit lagi", "jam lagi", "hari lagi", "persiapan", "agenda",
        "undangan", "wawancara", "interview", "kelas", "kuliah", "ujian", "kontrol", "checkup"
    )

    /** Pola jam (09:00 / 9.30) atau tanggal singkat (12/7). */
    private val TIME_PATTERN = Regex("\\b\\d{1,2}[:.]\\d{2}\\b|\\b\\d{1,2}/\\d{1,2}\\b")

    fun mightContainFinance(text: String): Boolean {
        val t = text.lowercase()
        return FINANCE_WORDS.any { t.contains(it) } || MONEY_PATTERN.containsMatchIn(t)
    }

    fun mightContainSchedule(text: String): Boolean {
        val t = text.lowercase()
        return SCHEDULE_WORDS.any { t.contains(it) } || TIME_PATTERN.containsMatchIn(t)
    }
}
