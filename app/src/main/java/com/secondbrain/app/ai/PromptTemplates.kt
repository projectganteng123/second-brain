package com.secondbrain.app.ai

/**
 * Template prompt untuk ekstraksi metadata. User boleh meng-override lewat Settings.
 * Placeholder WAJIB dipertahankan:
 *   {now}  -> waktu sekarang (yyyy-MM-dd HH:mm)
 *   {note} -> teks catatan mentah
 */
object PromptTemplates {

    const val PLACEHOLDER_NOW = "{now}"
    const val PLACEHOLDER_NOTE = "{note}"

    val DEFAULT_EXTRACTION = """
Kamu adalah asisten ekstraksi metadata dari catatan kerja dalam Bahasa Indonesia.

Waktu sekarang: {now}

Ekstrak metadata dari catatan berikut dan kembalikan HANYA JSON valid tanpa penjelasan apapun.

Catatan:
"{note}"

Kembalikan JSON dengan struktur berikut (isi yang relevan, kosongkan array/null bila benar-benar tidak ada):
{
  "title": "judul singkat kegiatan",
  "type": "meeting|task|reminder|event|note|idea|personal",
  "startTime": "HH:mm atau null",
  "endTime": "HH:mm atau null",
  "locations": [{"type": "location|platform", "value": "nama tempat"}],
  "entities": {
    "people": ["nama orang"],
    "organizations": ["nama organisasi/perusahaan"]
  },
  "keywords": ["kata kunci penting"],
  "recurrenceDates": ["YYYY-MM-DD"],
  "actions": [{"action": "deskripsi aksi", "owner": "nama atau null", "deadline": "YYYY-MM-DD atau null"}],
  "summary": "ringkasan 1-3 kalimat",
  "priority": "penting_urgen|penting_tidak_urgen|urgen_tidak_penting|tidak_penting_tidak_urgen",
  "status": "belum_mulai|berjalan|selesai",
  "preparationTime": "YYYY-MM-DDTHH:mm atau null"
}

Aturan WAJIB:
- Pengisian "recurrenceDates" tergantung "type":
  * Untuk type TERJADWAL (meeting, task, event, reminder): WAJIB diisi minimal satu tanggal.
      - Jika ada tanggal eksplisit/relatif (hari ini, besok, lusa, Senin depan, dll), konversi ke tanggal absolut berdasarkan waktu sekarang.
      - Jika berulang (setiap hari/minggu/bulan), buat daftar tanggalnya, maksimal 90 hari ke depan.
      - Jika TIDAK menyebut tanggal sama sekali, gunakan tanggal hari ini (dari waktu sekarang).
  * Untuk type TIDAK TERJADWAL (note, idea, personal): isi "recurrenceDates" HANYA jika catatan benar-benar menyebut tanggal/waktu. Jika tidak ada, biarkan array kosong [].
- Untuk waktu yang disebut samar tanpa jam pasti, petakan ke "startTime" berikut sebisanya:
  * "pagi"   -> 08:00
  * "siang"  -> 12:00
  * "sore"   -> 15:00
  * "malam"  -> 19:00
  * "subuh"  -> 05:00
  * "tengah hari" -> 12:00
  Jika ada jam pasti (mis. "jam 3 sore"), gunakan jam itu (15:00), abaikan pemetaan di atas.
- Jika tidak ada indikasi waktu sama sekali, "startTime" boleh null.
- "type" harus salah satu dari: meeting, task, reminder, event, note, idea, personal.
- "priority": tebak kuadran Eisenhower berdasarkan tingkat penting & mendesak dari isi catatan.
- "status": isi "belum_mulai" jika kegiatan belum dikerjakan atau bersifat akan datang/nanti. Isi "berjalan" atau "selesai" HANYA jika teks jelas menyatakannya.
- "preparationTime": isi HANYA jika pengguna secara eksplisit minta diingatkan untuk PERSIAPAN sebelum kegiatan (mis. "ingatkan sehari sebelumnya", "ingatkan 2 jam sebelum untuk siap-siap"). Hitung menjadi tanggal+jam absolut (yyyy-MM-ddTHH:mm) berdasarkan tanggal kegiatan dan waktu sekarang. Jika tidak diminta, isi null.
- Kembalikan HANYA JSON, tidak ada teks lain.
""".trimIndent()

    fun fill(template: String, now: String, note: String): String =
        template
            .replace(PLACEHOLDER_NOW, now)
            .replace(PLACEHOLDER_NOTE, note)
}
