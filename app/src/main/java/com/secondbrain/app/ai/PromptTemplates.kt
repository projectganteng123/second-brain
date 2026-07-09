package com.secondbrain.app.ai

/** Jenis prompt yang bisa di-override user lewat Settings. Tiga pertama = ekstraksi
 *  catatan (paralel); dua terakhir = fitur "Baca dengan AI". */
enum class ExtractionKind(val label: String) {
    COMBINED("Gabungan (1 prompt — hemat)"),
    UNIVERSAL("Universal"),
    FINANCE("Keuangan"),
    SCHEDULE("Acara / Pengingat"),
    MEDIA_READ("Baca gambar/PDF"),
    DOC_READ("Baca dokumen (Word/Excel/CSV)")
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
    const val PLACEHOLDER_GROUPS = "{groups}"

    /** Waktu sekarang termasuk NAMA HARI Indonesia — penting agar AI benar menghitung
     *  "Jumat depan" / "Senin besok" menjadi tanggal absolut. */
    fun nowString(): String {
        val now = java.time.LocalDateTime.now()
        val day = now.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL, java.util.Locale("id", "ID")
        )
        return "$day, ${now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"
    }

    /** Mode hemat (default): SATU prompt menghasilkan semua bagian sekaligus.
     *  Field-nya gabungan Universal + Keuangan + Acara, jadi satu respons bisa
     *  diparse oleh ketiga parser tanpa perubahan apa pun. */
    val DEFAULT_COMBINED = """
Ekstrak metadata LENGKAP dari catatan (Bahasa Indonesia) dalam SATU JSON. Kembalikan HANYA JSON tanpa teks lain.

WAKTU SEKARANG: {now}
(format: nama hari, tanggal, jam)

Gunakan waktu sekarang untuk mengubah SEMUA waktu relatif menjadi tanggal & jam absolut yang presisi sampai menit:
- besok = +1 hari; lusa = +2 hari; 10 menit lagi = +10 menit; setengah jam lagi = +30 menit; 2 jam lagi = +2 jam
- "Jumat ini", "Senin depan", dll dihitung berdasarkan nama hari saat ini.
Konversi waktu: subuh=05:00, pagi=08:00, siang=12:00, sore=15:00, malam=19:00, jam 1 siang=13:00, jam 7 malam=19:00.
Gunakan format 24 jam (HH:mm).

GRUP YANG SUDAH ADA: {groups}

Catatan:
"{note}"

Struktur JSON:
{
  "title": "judul singkat (maks 8 kata)",
  "type": "meeting|task|reminder|event|note|idea|personal",
  "summary": "1-3 kalimat",
  "keywords": ["kata penting"],
  "suggestedGroups": ["nama grup"],
  "locations": [{"type": "location|platform", "value": "tempat/aplikasi"}],
  "entities": {"people": ["nama"], "organizations": ["organisasi"]},
  "actions": [{"action": "aksi", "owner": "nama", "deadline": "YYYY-MM-DDTHH:mm|null"}],
  "priority": "penting_urgen|penting_tidak_urgen|urgen_tidak_penting|tidak_penting_tidak_urgen",
  "status": "belum_mulai|berjalan|selesai",
  "transactions": [
    {"type": "expense|income", "item": "", "category": "", "quantity": 1, "unit": null,
     "amount": 0, "currency": "IDR", "paymentMethod": null, "merchant": null,
     "person": null, "date": "YYYY-MM-DD|null", "notes": null}
  ],
  "schedules": [
    {"type": "meeting|task|event|reminder", "title": "judul kegiatan", "dates": ["YYYY-MM-DD"],
     "startTime": "HH:mm|null", "endTime": "HH:mm|null", "location": "tempat|null",
     "platform": "zoom/meet/dll|null", "participants": ["nama"],
     "priority": "penting_urgen|penting_tidak_urgen|urgen_tidak_penting|tidak_penting_tidak_urgen",
     "status": "belum_mulai|berjalan|selesai",
     "alarmTimes": ["YYYY-MM-DDTHH:mm"], "useAlarm": false}
  ]
}

Aturan umum:
- title maksimal 8 kata; summary 1-3 kalimat.
- keywords, locations, entities hanya berisi yang benar-benar muncul di catatan.
- suggestedGroups: pilih dari GRUP YANG SUDAH ADA yang paling sesuai (boleh lebih dari satu);
  boleh MAKSIMAL 1 nama grup BARU (2-4 kata) hanya jika catatan jelas bagian proyek/tema
  berulang dan tak ada grup yang cocok; tidak relevan → [].
- Jika people/owner tidak disebut, isi "saya".
- actions berisi semua tindakan; deadline hingga menit; tanggal tanpa jam → tugas 08:00, deadline 23:59; tidak ada → null.
- priority pakai matriks Eisenhower; status selesai hanya bila teks jelas menyatakannya.
- Array yang tidak ada isinya → [].

Aturan transactions:
- Satu transaksi = satu object; pecah kalimat berisi beberapa pembelian.
- quantity = jumlah barang; amount = TOTAL uang (angka murni). "5rb"=5000, "10k"=10000, "1,5jt"=1500000.
- currency default "IDR".
- category pilih dari: Belanja, Makanan, Minuman, Transportasi, Kesehatan, Pendidikan, Investasi,
  Tabungan, Hiburan, Tagihan, Gaji, Bonus, Hadiah, Bayar Hutang, Piutang, Donasi, Pajak, Asuransi,
  Perjalanan, Peralatan, Elektronik, Rumah Tangga, Lainnya.
- Tidak ada aktivitas keuangan → "transactions": [].

Aturan schedules:
- Hanya kegiatan bertipe meeting, task, event, reminder; satu kegiatan = satu object.
- Hari/tanggal tidak disebut → hari ini; dates wajib minimal satu tanggal.
- Tanggal tanpa jam → startTime "08:00"; deadline → "23:59"; endTime hanya jika disebut.
- Kegiatan berulang: JANGAN RRULE — isi dates dengan tanggal konkret maksimal 90 hari.
- participants tidak disebut → ["saya"].
- alarmTimes = semua waktu alarm/pengingat absolut:
  - "ingatkan 10 menit sebelumnya" pada meeting 12:00 → ["YYYY-MM-DDT11:50"];
  - permintaan pengingat berdiri sendiri ("ingatkan minum obat jam 20.00") → ["YYYY-MM-DDT20:00"];
  - tidak ada permintaan → [].
- useAlarm = true HANYA bila user eksplisit minta alarm keras/dibangunkan/jangan sampai terlewat;
  "ingatkan saya" saja tetap false.
- Tidak ada kegiatan → "schedules": [].
""".trimIndent()

    val DEFAULT_UNIVERSAL = """
Ekstrak metadata UMUM dari catatan (Bahasa Indonesia). Kembalikan HANYA JSON tanpa teks lain.

WAKTU SEKARANG: {now}
(digunakan hanya untuk mengubah deadline relatif pada action menjadi tanggal & jam absolut)

GRUP YANG SUDAH ADA: {groups}

Catatan:
"{note}"

Tujuan: ekstrak informasi umum. JANGAN ekstrak jadwal kegiatan, waktu acara, maupun transaksi keuangan (ditangani proses lain). Pengecualian: deadline pada action boleh diisi.

Struktur JSON:
{
  "title": "judul singkat",
  "type": "meeting|task|reminder|event|note|idea|personal",
  "summary": "1-3 kalimat",
  "keywords": ["kata penting"],
  "suggestedGroups": ["nama grup"],
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
- suggestedGroups: pilih dari GRUP YANG SUDAH ADA yang paling sesuai isi catatan (boleh lebih dari satu).
- suggestedGroups boleh berisi MAKSIMAL 1 nama grup BARU (2-4 kata) hanya jika catatan jelas bagian
  dari proyek/tema berulang dan tidak ada grup yang cocok di daftar.
- Jika tidak ada grup yang relevan, suggestedGroups = [].
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

    /** Prompt membaca gambar/PDF (struk, tulisan tangan, screenshot) — EKSTRAKSI LENGKAP. */
    val MEDIA_READ = """
Baca gambar/dokumen terlampir dengan TELITI (konteks Indonesia). Tugas utamamu adalah
EKSTRAKSI TEKS LENGKAP: salin SEMUA teks yang terbaca, tanpa meringkas dan tanpa
mengurangi apa pun. Periksa ulang hasil bacaanmu sebelum menjawab.

Kembalikan HANYA JSON, tanpa teks lain:
{"source": "jenis sumber", "text": "seluruh teks hasil ekstraksi"}

Aturan:
- source: sebut jenisnya singkat, mis. "struk belanja", "catatan tulis tangan",
  "screenshot chat", "poster acara", "dokumen".
- text: LENGKAP, pertahankan urutan asli:
  - Struk/nota: SETIAP baris item dengan jumlah & harga persis angkanya, subtotal,
    diskon, pajak, total, nama toko, tanggal & jam struk.
  - Catatan tulis tangan: salin kata per kata.
  - Screenshot chat/pesan: salin percakapan utuh, satu baris per pesan, sertakan nama
    pengirim dan jam bila terlihat.
  - Poster/undangan/dokumen: salin semua teks yang terlihat.
- Boleh memperbaiki ejaan/typo yang JELAS salah baca; DILARANG meringkas, membuang
  baris, atau menambah informasi yang tidak ada di gambar.
- Angka, nominal uang, tanggal, dan jam WAJIB persis seperti sumber — jangan menebak.
- Bagian yang benar-benar tidak terbaca tulis sebagai [tidak terbaca], jangan dikarang.
""".trimIndent()

    /** Prompt membaca dokumen teks (Word/Excel/CSV/TXT). Placeholder: {jenis} & {note}. */
    val DOC_READ = """
Berikut isi file {jenis}. Tugasmu adalah mengembalikan teksnya secara LENGKAP dan rapi —
tanpa meringkas dan tanpa membuang baris. Periksa ulang sebelum menjawab.

Kembalikan HANYA JSON, tanpa teks lain:
{"source": "jenis dokumen singkat", "text": "seluruh isi"}

Aturan:
- source: mis. "dokumen Word", "tabel Excel", "data CSV".
- text: seluruh isi apa adanya. Boleh merapikan format dan memperbaiki typo yang jelas,
  tapi DILARANG meringkas, menghilangkan baris, atau menambah informasi yang tidak ada.
- Semua angka, nominal uang, tanggal, jam, dan nama WAJIB persis seperti sumber.
- Tabel: pertahankan tiap baris (mis. "Roti | 2 | 18000").

Isi file:
<<<
{note}
>>>
""".trimIndent()

    fun fillDocRead(template: String, kind: String, content: String): String =
        template.replace("{jenis}", kind).replace(PLACEHOLDER_NOTE, content)

    fun defaultFor(kind: ExtractionKind): String = when (kind) {
        ExtractionKind.COMBINED -> DEFAULT_COMBINED
        ExtractionKind.UNIVERSAL -> DEFAULT_UNIVERSAL
        ExtractionKind.FINANCE -> DEFAULT_FINANCE
        ExtractionKind.SCHEDULE -> DEFAULT_SCHEDULE
        ExtractionKind.MEDIA_READ -> MEDIA_READ
        ExtractionKind.DOC_READ -> DOC_READ
    }

    fun fill(template: String, now: String, note: String, groups: List<String> = emptyList()): String =
        template
            .replace(PLACEHOLDER_NOW, now)
            .replace(PLACEHOLDER_NOTE, note)
            .replace(PLACEHOLDER_GROUPS, com.secondbrain.app.data.GsonProvider.gson.toJson(groups))

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
