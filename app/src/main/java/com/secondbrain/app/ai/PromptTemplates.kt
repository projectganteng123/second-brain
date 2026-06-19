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
Ekstrak metadata dari catatan (Bahasa Indonesia). Kembalikan HANYA JSON, tanpa teks lain.

WAKTU SEKARANG: {now}
Gunakan waktu sekarang ini sebagai acuan untuk MENGUBAH semua ungkapan waktu relatif menjadi
tanggal & jam absolut yang PRESISI sampai menit. Contoh (relatif terhadap waktu sekarang):
"besok" -> tanggal +1 hari; "sore" -> 15:00; "jam 3 sore" -> 15:00; "2 jam lagi" -> sekarang + 2 jam;
"setengah jam lagi" -> sekarang + 30 menit; "10 menit lagi" -> sekarang + 10 menit; "lusa pagi" -> +2 hari 08:00.
Jam wajib format HH:mm 24 jam.

Catatan:
"{note}"

Struktur JSON (kosongkan array / null bila tidak ada):
{
  "title": "judul singkat",
  "type": "meeting|task|reminder|event|note|idea|personal",
  "startTime": "HH:mm | null",
  "endTime": "HH:mm | null",
  "locations": [{"type": "location|platform", "value": "tempat"}],
  "entities": {"people": ["nama"], "organizations": ["organisasi"]},
  "keywords": ["kata kunci"],
  "recurrenceDates": ["YYYY-MM-DD"],
  "actions": [{"action": "aksi", "owner": "nama|null", "deadline": "YYYY-MM-DD|null"}],
  "summary": "1-3 kalimat",
  "priority": "penting_urgen|penting_tidak_urgen|urgen_tidak_penting|tidak_penting_tidak_urgen",
  "status": "belum_mulai|berjalan|selesai",
  "preparationTime": "YYYY-MM-DDTHH:mm | null"
}

Aturan:
- recurrenceDates: untuk type meeting/task/event/reminder WAJIB minimal 1 tanggal (jika tak disebut, pakai tanggal hari ini; jika berulang, daftar tanggal maks 90 hari). Untuk note/idea/personal isi hanya bila ada tanggal.
- waktu samar tanpa jam: pagi=08:00, siang=12:00, sore=15:00, malam=19:00, subuh=05:00. Jika ada jam pasti, pakai jam itu.
- priority: tebak kuadran Eisenhower (penting & mendesak).
- status: "belum_mulai" untuk kegiatan akan datang/belum dikerjakan; "berjalan"/"selesai" hanya bila teks jelas.
- preparationTime: isi HANYA bila user minta diingatkan persiapan (mis. "ingatkan sehari sebelumnya"); hitung tanggal+jam absolut.
""".trimIndent()

    fun fill(template: String, now: String, note: String): String =
        template
            .replace(PLACEHOLDER_NOW, now)
            .replace(PLACEHOLDER_NOTE, note)
}
