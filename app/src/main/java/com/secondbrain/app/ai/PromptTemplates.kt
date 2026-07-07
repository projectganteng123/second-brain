package com.secondbrain.app.ai

/** Jenis prompt ekstraksi — satu catatan diproses TIGA prompt paralel lalu hasilnya digabung. */
enum class ExtractionKind(val label: String) {
    UNIVERSAL("Universal"),
    FINANCE("Keuangan"),
    SCHEDULE("Acara / Pengingat")
}

/**
 * Template prompt untuk ekstraksi metadata. User boleh meng-override lewat Settings (per jenis).
 * Placeholder:
 *   {now}  -> waktu sekarang ("Senin, yyyy-MM-dd HH:mm") — WAJIB di prompt Acara
 *   {note} -> teks catatan mentah — WAJIB di semua prompt
 */
object PromptTemplates {

    const val PLACEHOLDER_NOW = "{now}"
    const val PLACEHOLDER_NOTE = "{note}"

    /** Waktu sekarang termasuk NAMA HARI Indonesia — penting agar AI benar menghitung
     *  "Jumat depan" / "Senin besok" menjadi tanggal absolut. */
    fun nowString(): String {
        val now = java.time.LocalDateTime.now()
        val day = now.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL, java.util.Locale("id", "ID")
        )
        return "$day, ${now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
    }

    val DEFAULT_UNIVERSAL = """
Ekstrak metadata UMUM dari catatan (Bahasa Indonesia). Kembalikan HANYA JSON, tanpa teks lain.

WAKTU SEKARANG: {now}
(dipakai hanya untuk mengubah deadline relatif pada action item menjadi tanggal absolut)

Catatan:
"{note}"

Tujuan: informasi umum. JANGAN ekstrak waktu kegiatan, jadwal, maupun transaksi keuangan
(itu ditangani proses lain). Pengecualian: deadline pada action item boleh diisi.

Struktur JSON (kosongkan array / null bila tidak ada):
{
  "title": "judul singkat",
  "type": "meeting|task|reminder|event|note|idea|personal",
  "summary": "1-3 kalimat",
  "keywords": ["kata penting"],
  "locations": [{"type": "location|platform", "value": "tempat/aplikasi"}],
  "entities": {"people": ["nama"], "organizations": ["organisasi"]},
  "actions": [{"action": "aksi", "owner": "nama|null", "deadline": "YYYY-MM-DD|null"}],
  "priority": "penting_urgen|penting_tidak_urgen|urgen_tidak_penting|tidak_penting_tidak_urgen",
  "status": "belum_mulai|berjalan|selesai"
}

Aturan:
- title maksimal 8 kata.
- keywords berisi kata penting, termasuk nama produk/proyek bila ada.
- entities hanya berisi entitas yang benar-benar muncul di catatan.
- type: pilih yang paling sesuai isi catatan.
- priority: tebak kuadran Eisenhower (penting & mendesak).
- status: "belum_mulai" bila belum dikerjakan; "berjalan"/"selesai" hanya bila teks jelas.
""".trimIndent()

    val DEFAULT_FINANCE = """
Ekstrak seluruh transaksi keuangan dari catatan (Bahasa Indonesia). Kembalikan HANYA JSON, tanpa teks lain.

WAKTU SEKARANG: {now}
(dipakai untuk mengubah tanggal relatif seperti "kemarin"/"tadi pagi" menjadi tanggal absolut)

Catatan:
"{note}"

Struktur JSON:
{
  "transactions": [
    {
      "type": "expense|income",
      "item": "",
      "category": "",
      "quantity": 1,
      "unit": null,
      "amount": 0,
      "currency": "IDR",
      "paymentMethod": null,
      "merchant": null,
      "person": null,
      "date": "YYYY-MM-DD|null",
      "notes": null
    }
  ]
}

Aturan:
- Satu transaksi = satu object; harus bisa mengekstrak BANYAK transaksi sekaligus.
- Jika satu kalimat berisi beberapa pembelian, pecah menjadi beberapa transaksi.
- quantity = jumlah barang. amount = TOTAL uang transaksi (angka murni tanpa pemisah ribuan).
- Pahami singkatan: "5rb"=5000, "10k"=10000, "1,5jt"=1500000.
- currency default "IDR" bila tidak disebut.
- category pilih paling sesuai dari: Belanja, Makanan, Minuman, Transportasi, Kesehatan,
  Pendidikan, Investasi, Tabungan, Hiburan, Tagihan, Gaji, Bonus, Hadiah, Bayar Hutang,
  Piutang, Donasi, Pajak, Asuransi, Perjalanan, Peralatan, Elektronik, Rumah Tangga, Lainnya.
- Tidak ada aktivitas keuangan -> {"transactions": []}.
""".trimIndent()

    val DEFAULT_SCHEDULE = """
Ekstrak SEMUA kegiatan terjadwal & pengingat dari catatan (Bahasa Indonesia). Kembalikan HANYA JSON, tanpa teks lain.

WAKTU SEKARANG: {now}
(format: nama hari, tanggal, jam)
Gunakan sebagai acuan untuk MENGUBAH semua waktu relatif menjadi tanggal & jam absolut
yang PRESISI sampai menit: "besok" -> +1 hari; "lusa" -> +2 hari; "10 menit lagi" -> sekarang
+ 10 menit; "setengah jam lagi" -> +30 menit; "2 jam lagi" -> +2 jam. Gunakan NAMA HARI di atas
untuk menghitung sebutan hari: mis. sekarang Senin, "Jumat ini" -> +4 hari, "Senin depan" -> +7 hari.
Pastikan pergeseran hari & tanggal benar.
Waktu samar: subuh=05:00, pagi=08:00, siang=12:00, sore=15:00, malam=19:00.
Jika ada jam pasti, pakai jam itu. Jam wajib format HH:mm 24 jam.

Catatan:
"{note}"

Struktur JSON:
{
  "schedules": [
    {
      "type": "meeting|task|event|reminder",
      "title": "judul kegiatan",
      "dates": ["YYYY-MM-DD"],
      "startTime": "HH:mm|null",
      "endTime": "HH:mm|null",
      "location": "tempat|null",
      "platform": "zoom/meet/dll|null",
      "participants": ["nama"],
      "priority": "penting_urgen|penting_tidak_urgen|urgen_tidak_penting|tidak_penting_tidak_urgen",
      "status": "belum_mulai|berjalan|selesai",
      "preparationTimes": ["YYYY-MM-DDTHH:mm"],
      "useAlarm": false
    }
  ]
}

Aturan:
- Harus bisa mengekstrak BANYAK kegiatan sekaligus; satu kegiatan = satu object.
- dates WAJIB minimal 1 tanggal per kegiatan (tidak disebut -> tanggal hari ini).
- Kegiatan BERULANG ("setiap Senin", "tiap bulan tanggal 10"): JANGAN pakai RRULE —
  daftarkan tanggal konkretnya satu per satu di dates, maksimal 90 hari ke depan.
- Tanggal tanpa jam -> startTime "08:00". Deadline tanpa jam -> "23:59".
- preparationTimes: isi HANYA bila user minta diingatkan persiapan (mis. "ingatkan sehari
  sebelumnya"); hitung tanggal+jam absolut. Boleh LEBIH DARI SATU waktu bila user minta
  beberapa pengingat; array kosong bila tidak diminta. Tiap waktu akan menjadi alarm.
- useAlarm: true HANYA bila user minta alarm keras / dibangunkan / jangan sampai terlewat
  (mis. "pakai alarm", "bangunkan saya").
- Tidak ada kegiatan terjadwal maupun pengingat -> {"schedules": []}.
""".trimIndent()

    /** Prompt membaca gambar/dokumen (struk, tulisan tangan, screenshot, PDF) jadi teks catatan. */
    val MEDIA_READ = """
Baca isi gambar/dokumen terlampir (konteks Indonesia). Kembalikan HANYA JSON, tanpa teks lain:
{"source": "jenis sumber", "text": "isi penting"}

Aturan:
- source: sebut jenisnya singkat, mis. "struk belanja", "catatan tulis tangan", "screenshot chat", "poster acara", "dokumen".
- text: Bahasa Indonesia, padat, siap diproses sebagai catatan:
  - Struk/nota: tulis tiap item beserta harganya (angka murni, mis. "Roti 18000"), jumlah bila ada, total, nama toko, dan tanggal struk.
  - Catatan tulis tangan: salin isi teksnya (rapikan seperlunya, jangan ubah makna).
  - Screenshot chat/pesan: rangkum percakapan + informasi penting (janji, waktu, tempat, nominal uang).
  - Poster/undangan: nama acara, tanggal, jam, tempat.
- Jangan mengarang; lewati bagian yang tidak terbaca.
""".trimIndent()

    fun defaultFor(kind: ExtractionKind): String = when (kind) {
        ExtractionKind.UNIVERSAL -> DEFAULT_UNIVERSAL
        ExtractionKind.FINANCE -> DEFAULT_FINANCE
        ExtractionKind.SCHEDULE -> DEFAULT_SCHEDULE
    }

    fun fill(template: String, now: String, note: String): String =
        template
            .replace(PLACEHOLDER_NOW, now)
            .replace(PLACEHOLDER_NOTE, note)

    /** Prompt tanya-jawab, dipakai semua provider. */
    fun qaPrompt(question: String, contextNotes: List<String>): String {
        val context = contextNotes.mapIndexed { i, n -> "[${i + 1}] $n" }.joinToString("\n\n")
        return """
Kamu adalah asisten pribadi yang menjawab pertanyaan berdasarkan catatan pengguna.

Pertanyaan: "$question"

Catatan yang relevan:
$context

Jawab pertanyaan secara ringkas dan langsung berdasarkan catatan di atas.
Jika informasi tidak tersedia dalam catatan, katakan dengan jelas.
Gunakan Bahasa Indonesia.
""".trimIndent()
    }
}
