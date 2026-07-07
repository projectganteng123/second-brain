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
Ekstrak metadata UMUM dari catatan (Bahasa Indonesia). Kembalikan HANYA JSON tanpa teks lain.

WAKTU SEKARANG: {now}
(digunakan hanya untuk mengubah deadline relatif pada action menjadi tanggal & jam absolut)

Catatan:
"{note}"

Tujuan: ekstrak informasi umum. JANGAN ekstrak jadwal kegiatan, waktu acara, maupun transaksi keuangan (ditangani proses lain). Pengecualian: deadline pada action boleh diisi.

Struktur JSON:
{
  "title": "judul singkat",
  "type": "meeting|task|reminder|event|note|idea|personal",
  "summary": "1-3 kalimat",
  "keywords": ["kata penting"],
  "locations": [
    {
      "type": "location|platform",
      "value": "tempat/aplikasi"
    }
  ],
  "entities": {
    "people": ["nama"],
    "organizations": ["organisasi"]
  },
  "actions": [
    {
      "action": "aksi",
      "owner": "nama",
      "deadline": "YYYY-MM-DDTHH:mm|null"
    }
  ],
  "priority": "penting_urgen|penting_tidak_urgen|urgen_tidak_penting|tidak_penting_tidak_urgen",
  "status": "belum_mulai|berjalan|selesai"
}

Aturan:
- title maksimal 8 kata.
- summary 1-3 kalimat singkat.
- keywords berisi kata penting, termasuk nama produk, proyek, atau topik bila ada.
- locations hanya berisi tempat atau platform yang benar-benar disebut.
- entities hanya berisi entitas yang benar-benar muncul di catatan.
- Jika people tidak disebut atau tidak jelas, isi dengan ["saya"].
- type pilih yang paling sesuai isi catatan.
- actions berisi semua tindakan yang harus dilakukan.
- owner diisi nama yang bertanggung jawab; jika tidak disebut, isi "saya".
- deadline diisi hingga menit (YYYY-MM-DDTHH:mm).
- Ubah deadline relatif menjadi waktu absolut berdasarkan WAKTU SEKARANG.
- Jika hanya disebut tanggal tanpa jam, gunakan:
  - tugas biasa → 08:00
  - deadline → 23:59
- Konversi waktu:
  - subuh=05:00
  - pagi=08:00
  - siang=12:00
  - sore=15:00
  - malam=19:00
  - jam 1 siang = 13:00
  - jam 7 malam / 07.00 malam = 19:00
- Jika deadline tidak ada, isi null.
- priority diperkirakan menggunakan matriks Eisenhower.
- status:
  - belum_mulai jika belum dikerjakan atau hanya berupa rencana.
  - berjalan jika sedang dikerjakan.
  - selesai hanya jika teks secara jelas menyatakan sudah selesai.
- Jika tidak ada action, gunakan [].
- Jika tidak ada organizations, locations, atau keywords, gunakan array kosong.
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
Ekstrak SEMUA kegiatan terjadwal dari catatan (Bahasa Indonesia). Kembalikan HANYA JSON tanpa teks lain.

WAKTU SEKARANG: {now}
(format: nama hari, tanggal, jam)

Gunakan waktu sekarang untuk mengubah SEMUA waktu relatif menjadi tanggal & jam absolut yang presisi sampai menit.
Contoh:
- besok = +1 hari
- lusa = +2 hari
- 10 menit lagi = +10 menit
- setengah jam lagi = +30 menit
- 2 jam lagi = +2 jam
- "Jumat ini", "Senin depan", dll dihitung berdasarkan nama hari saat ini.

Konversi waktu:
- subuh = 05:00
- pagi = 08:00
- siang = 12:00
- sore = 15:00
- malam = 19:00
- jam 1 siang = 13:00
- jam 7 malam / 07.00 malam = 19:00
Gunakan format 24 jam (HH:mm).

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
      "alarmTimes": ["YYYY-MM-DDTHH:mm"],
      "useAlarm": false
    }
  ]
}

Aturan:
- Ekstrak hanya kegiatan bertipe meeting, task, event, atau reminder. Jika catatan bukan kegiatan tersebut, abaikan.
- Satu kegiatan = satu object.
- Jika hari/tanggal tidak disebut, gunakan hari ini.
- dates wajib berisi minimal satu tanggal.
- Jika hanya ada tanggal tanpa jam:
  - meeting/task/event/reminder → startTime = "08:00"
  - jika merupakan deadline → startTime = "23:59"
- endTime diisi hanya jika disebut.
- Untuk kegiatan berulang (mis. setiap Senin, tiap tanggal 10), jangan gunakan RRULE. Isi dates dengan tanggal konkret maksimal 90 hari ke depan.
- participants: jika tidak disebut, isi ["saya"].
- alarmTimes berisi semua waktu alarm atau pengingat dalam bentuk waktu absolut.
  - Jika pengguna meminta diingatkan sebelum kegiatan, isi dengan waktu pengingat.
    Contoh:
    - Meeting 12:00, "ingatkan 10 menit sebelumnya" → ["YYYY-MM-DDT11:50"]
    - "ingatkan malam sebelumnya" → ["YYYY-MM-DDT19:00"] pada hari sebelumnya.
  - Jika catatan hanya berupa permintaan mengingatkan (bukan alarm sebelum acara), isi alarmTimes dengan waktu pengingat tersebut.
    Contoh:
    - "Ingatkan saya minum obat jam 20.00" → alarmTimes = ["YYYY-MM-DDT20:00"]
    - "Besok jam 09.00 ingatkan saya menghubungi Budi" → alarmTimes = ["YYYY-MM-DDT09:00"]
  - Jika tidak ada permintaan alarm atau pengingat, gunakan [].
- useAlarm = true hanya jika pengguna secara eksplisit meminta alarm keras, dibangunkan, jangan sampai terlewat, atau ungkapan serupa. Permintaan "ingatkan saya" saja tetap bernilai false.
- Jika tidak ditemukan kegiatan → {"schedules":[]}.
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

    /** Prompt merangkum isi dokumen (Word/Excel/CSV/TXT) yang teksnya sudah diekstrak lokal. */
    fun docReadPrompt(kind: String, content: String): String = """
Berikut isi file $kind. Kembalikan HANYA JSON, tanpa teks lain:
{"source": "jenis dokumen singkat", "text": "isi penting"}

Aturan:
- source: mis. "dokumen Word", "tabel Excel", "data CSV".
- text: Bahasa Indonesia, padat, siap diproses sebagai catatan.
- Pertahankan angka, nominal uang, tanggal, jam, dan nama persis seperti sumber.
- Tabel: sebutkan tiap baris penting (mis. "Roti 18000"), plus total bila ada.
- Jangan mengarang isi yang tidak ada.

Isi file:
<<<
$content
>>>
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
