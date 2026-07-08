# Second Brain — Daftar Fitur & Panduan Testing

Dokumen ini berisi daftar fitur aplikasi **Second Brain** (hasil sesi MVP) beserta panduan testing detail.
Sebagian besar tes bisa dilakukan dengan **copy-paste teks** langsung ke layar Input.

> **Tanggal acuan tes: Jumat, 19 Juni 2026.**
> Semua teks tes dirancang relatif ke tanggal ini supaya hasil ekstraksi AI bisa diverifikasi:
> - Besok = Sabtu 20 Jun
> - Lusa = Minggu 21 Jun
> - Senin depan = 22 Jun
> - Rabu depan = 24 Jun

---

## A. Daftar Fitur

### Capture / Input
1. **Input teks manual** + **voice-first STT** (mic besar default, mic kecil selalu ada, hasil di-append, bahasa id-ID)
2. **Template Capture Berpemandu** — chip template + tombol-pertanyaan, STT serial, hasil jadi label inline `[Label: hasil]`, Pengaman 1 (kursor lompat ke ruang kosong) & Pengaman 2 (blok yang akan diganti disorot)
3. **QuickCaptureWidget** homescreen → buka langsung layar Input

### Ekstraksi AI
4. **Ekstraksi metadata via Gemini** → 10 field (judul, jenis, jam, tanggal, prioritas, status, recurrenceDates, actions, preparationTime, dll.)
5. **Resolusi waktu**: waktu samar (pagi/siang/sore/malam) → jam konkret; waktu relatif (besok/lusa/minggu depan) → tanggal presisi
6. **recurrenceDates** untuk tipe terjadwal (meeting/task/event/reminder), maks 90 hari; kosong untuk note/idea/personal kecuali ada tanggal
7. **Model ladder otomatis** (flash-lite → flash → flash-latest) + fallback antar key×model saat 429
8. **Custom prompt ekstraksi** (Settings, dengan validasi placeholder `{now}`/`{note}`)
9. **PendingProcessor** — reproses catatan offline saat app dibuka

### Kelola Catatan
10. **Preview + edit metadata manual** (judul/jenis/jam/tanggal, prioritas/status, preparationTime)
11. **NoteDetailScreen** — edit, re-extract, arsip, hapus, ubah prioritas-status
12. **Prioritas Eisenhower** (4 kuadran) + **Status** (belum_mulai / berjalan / selesai)
13. **AllNotesScreen** — filter Semua / Terjadwal / Tanpa tanggal + filter jenis
14. **Search**
15. **ArchiveScreen** (lihat / kembalikan arsip)
16. **ActionItemsScreen** — agregasi action items lintas catatan
17. **GanttScreen** — timeline 14 hari, horizontal-scroll, ketuk batang → detail

### Pengingat
18. **ReminderWorker** (WorkManager periodic 1 jam + enqueueNow setelah simpan)
19. **AlarmManager exact** via ReminderScheduler (`setAlarmClock`, kebal Doze) — dijadwalkan langsung saat simpan
20. **Offset reminder** (chips di Settings)
21. **preparationTime** — reminder persiapan terpisah
22. **Alarm per-catatan** (full-screen intent + suara alarm + getar)

### Q&A / RAG
23. **QaScreen** — retrieval lokal + jawaban Gemini dengan **sitasi sumber**

### Data & Utilitas
24. **Export JSON/CSV** (SAF, kolom metadata lengkap)
25. **Multi API key** (satu per baris di Settings)
26. **Debug Log panel** (Settings → Debug Log) — request/response AI + operasi DB

---

## B. Skenario Testing

### B1. Tes Copy-Paste (langsung ke layar Input)

Untuk tiap tes: buka **Input → tempel teks → proses (ekstraksi AI) → cek hasil di Preview → Simpan**.

#### TES 1 — Meeting terjadwal, waktu samar → jam konkret
```
Besok pagi meeting dengan tim marketing bahas campaign Q3 di ruang rapat lantai 3
```
- [ ] Jenis = **meeting**
- [ ] Tanggal = **Sabtu, 20 Jun 2026**
- [ ] Jam = jam konkret pagi (mis. **09:00**), bukan teks "pagi"
- [ ] recurrenceDates **terisi** (1 tanggal)
- [ ] Judul ringkas, mis. "Meeting tim marketing — campaign Q3"

#### TES 2 — Task berulang (recurring) → recurrenceDates banyak
```
Setiap hari Senin jam 8 pagi standup mingguan tim engineering
```
- [ ] Jenis = **task / meeting**
- [ ] recurrenceDates = semua hari Senin dari 22 Jun hingga **maks 90 hari**
- [ ] Jam = **08:00**

#### TES 3 — Reminder dengan waktu persiapan (preparationTime)
```
Lusa jam 3 sore ada interview kandidat developer, siapkan diri 30 menit sebelumnya
```
- [ ] Tanggal = **Minggu, 21 Jun 2026**, jam = **15:00**
- [ ] preparationTime terisi → reminder persiapan **14:30**
- [ ] (verifikasi device) muncul 2 trigger: persiapan + acara

#### TES 4 — Catatan tanpa tanggal (idea) → harus tetap terlihat
```
Ide: bikin fitur dark mode biar hemat baterai dan enak dipakai malam hari
```
- [ ] Jenis = **idea / note**
- [ ] recurrenceDates / tanggal = **kosong**
- [ ] Setelah simpan → AllNotes → filter "Tanpa tanggal" → catatan ini muncul (regresi penting "opsi B")

#### TES 5 — Ekstraksi multiple action items
```
Setelah meeting tadi: kirim invoice ke klien PT Maju, follow up email vendor hosting, update slide presentasi sebelum hari Jumat, dan booking ruang meeting untuk minggu depan
```
- [ ] Field **actions** berisi **4 item** terpisah
- [ ] Setelah simpan → ActionItemsScreen → keempat item muncul dalam agregasi

#### TES 6 — Prioritas Eisenhower (penting + mendesak)
```
Penting dan mendesak: bayar pajak kantor sebelum tanggal 30 Juni, kalau telat kena denda
```
- [ ] Prioritas = kuadran **Penting & Mendesak**
- [ ] Tanggal/deadline = **30 Jun 2026**
- [ ] Status default = **belum_mulai**

#### TES 7 — Resolusi waktu relatif presisi menit
```
Minggu depan Rabu jam setengah 11 pagi kontrol ke dokter gigi
```
- [ ] Tanggal = **Rabu, 24 Jun 2026**
- [ ] Jam = **10:30** (bukan 11:30)

#### TES 8 — Catatan campuran (uji disambiguasi tipe)
```
Catatan pribadi: anniversary tanggal 5 Juli, jangan lupa pesan kue dan booking restoran dari sekarang
```
- [ ] Jenis condong **personal / event**
- [ ] Tanggal event = **5 Jul 2026**
- [ ] actions = pesan kue + booking restoran

#### TES 9 — Template Capture Berpemandu (label inline)
Buka Input → pilih **chip template** → klik **tombol pertanyaan**. Untuk tes tanpa suara, tempel teks berformat label berikut:
```
[Apa: presentasi ke investor] [Kapan: Senin depan jam 2 siang] [Di mana: kantor pusat] [Persiapan: siapkan deck 1 jam sebelum]
```
- [ ] Label `[...]` berwarna (VisualTransformation)
- [ ] Jenis meeting/event, tanggal **22 Jun 2026**, jam **14:00**, preparationTime 1 jam sebelum (**13:00**)

#### TES 10 — Q&A / RAG dengan sitasi (jalankan SETELAH Tes 1, 3, 7 tersimpan)
Buka **QaScreen**, tempel pertanyaan satu per satu:
```
Kapan jadwal meeting dengan tim marketing?
```
```
Acara apa saja yang saya punya minggu ini?
```
```
Kapan saya kontrol ke dokter gigi?
```
- [ ] Jawaban benar sesuai catatan tersimpan
- [ ] Ada **sitasi sumber** (menunjuk catatan asal), bukan halusinasi

---

### B2. Tes Observasi di Device (tidak murni copy-paste)

| # | Fitur | Langkah | Yang dicek | OK? |
|---|-------|---------|-----------|-----|
| 11 | **Voice input STT** | Input → ketuk mic besar → ucapkan: *"besok jam 10 pagi meeting dengan klien"* | Transkrip id-ID muncul & di-append; ekstraksi jalan | [ ] |
| 12 | **STT serial (template)** | Template → tombol pertanyaan → cue rekam → bicara → Selesai | Mic hidup setelah cue; hasil kosong tidak menyisipkan apa pun; tidak ada sesi dobel | [ ] |
| 13 | **Alarm exact + full-screen** | Buat catatan jam +5 menit, aktifkan Opsi Alarm per-catatan, kunci layar | Tepat waktu: full-screen + suara alarm + getar; kebal Doze | [ ] |
| 14 | **Reminder + offset** | Settings → set offset (mis. 15 menit). Buat acara 20 menit lagi | Notifikasi muncul ~offset sebelum acara | [ ] |
| 15 | **Reminder persiapan** | Pakai catatan Tes 3 | Dua trigger: persiapan (14:30) + acara (15:00) | [ ] |
| 16 | **Gantt 14 hari** | Header dashboard → ikon Timeline | Batang berwarna sesuai tipe, scroll horizontal, ketuk → NoteDetail | [ ] |
| 17 | **Arsip & kembalikan** | NoteDetail → Arsip → ArchiveScreen → Kembalikan | Hilang dari dashboard saat diarsip, balik saat dikembalikan | [ ] |
| 18 | **Edit & re-extract** | NoteDetail → ubah teks → Re-extract | Metadata ter-update; tidak crash ("Simpan & proses ulang") | [ ] |
| 19 | **Export JSON & CSV** | Settings → Export → pilih lokasi (SAF) | File terbuat; CSV kolom lengkap; JSON valid | [ ] |
| 20 | **QuickCaptureWidget** | Pasang widget di homescreen → ketuk | Langsung buka layar Input | [ ] |
| 21 | **PendingProcessor offline** | Matikan internet → simpan catatan → nyalakan internet → buka ulang app | Catatan otomatis diproses ekstraksi | [ ] |
| 22 | **Fallback 429 / model ladder** | Settings → Debug Log, lalu proses beberapa catatan beruntun | Log menunjukkan model naik tangga / pindah key; pesan jelas kalau semua habis | [ ] |
| 23 | **Multi API key** | Settings → isi 2 key (satu per baris) | Key kedua dipakai saat key pertama 429 | [ ] |
| 24 | **Custom prompt** | Settings → ubah prompt (sertakan `{now}` & `{note}`) → proses | Validasi placeholder jalan; hasil ikut prompt baru | [ ] |
| 25 | **Search** | Search → ketik kata kunci dari catatan tersimpan | Hasil relevan muncul | [ ] |

---

### B3. Tes Regresi / Edge Case

#### TES 26 — NoteType case-insensitive (regresi: dulu semua jadi NOTE)
```
MEETING dengan direktur besok jam 9
```
```
task: beli tinta printer hari ini
```
- [ ] Jenis ter-parse benar (meeting / task), bukan jatuh semua ke "note"

#### TES 27 — Horizon recurrence >90 hari
```
Setiap tanggal 1 bayar cicilan mobil
```
- [ ] recurrenceDates berhenti di ±90 hari, tidak tak terbatas

#### TES 28 — Catatan kosong / sampah
```
asdf
```
- [ ] App tidak crash; tetap simpan sebagai note minimal atau beri pesan jelas

---

### B4. Tes Fitur Grup Catatan (v4)

#### TES 29 — Migrasi DB 3→4 (data lama selamat)
Update APK di atas instalasi lama (JANGAN uninstall).
- [ ] App terbuka normal, semua catatan & pengingat lama utuh
- [ ] Ikon folder (Grup) muncul di halaman daftar catatan

#### TES 30 — Saran grup dari AI (cold start → grup baru)
Belum ada grup sama sekali. Input:
```
Meeting vendor cat tembok untuk renovasi rumah, Jumat depan jam 10
```
- [ ] Di Preview muncul chip grup usulan mis. "Renovasi Rumah (baru)" TIDAK tercentang
- [ ] Centang chip → Simpan → layar Grup menampilkan grup baru berisi 1 catatan

#### TES 31 — Saran grup cocok existing (pra-centang)
Setelah TES 30, input catatan lain bertema sama:
```
Beli keramik lantai 40x40 untuk renovasi, budget 5 juta
```
- [ ] Chip "Renovasi Rumah" muncul TERCENTANG otomatis (bukan "(baru)")
- [ ] Simpan tanpa mengubah apa pun → catatan masuk grup yang sama

#### TES 32 — Multi-grup + kelola dari Detail
- [ ] Preview: + Grup → buat "Keuangan Juli" → catatan masuk 2 grup
- [ ] Detail catatan: kedua chip grup tampil; ✕ melepas dari grup; ketuk chip membuka isi grup

#### TES 33 — Jalur pending (offline)
Matikan internet → simpan catatan → nyalakan internet → buka app (PendingProcessor jalan) → buka Detail catatan itu.
- [ ] Baris "Saran AI — ketuk untuk menerima:" muncul (bila AI menyarankan)
- [ ] Ketuk = masuk grup; ✕ = saran hilang tanpa masuk grup; tidak ada yang otomatis

#### TES 34 — Kelola grup
- [ ] Rename grup → nama berubah di semua tempat, keanggotaan tetap
- [ ] Rename ke nama yang sudah dipakai → ditolak dengan pesan
- [ ] Hapus grup → dialog menyebut jumlah catatan; catatan TIDAK terhapus
- [ ] Hapus catatan → hilang dari isi grup (jumlah berkurang)
- [ ] Buat grup "renovasi rumah" (huruf kecil) saat "Renovasi Rumah" sudah ada → TIDAK jadi grup kembar

#### TES 35 — Prompt custom tanpa {groups}
Settings → edit prompt Universal, hapus baris GRUP YANG SUDAH ADA.
- [ ] Ekstraksi tetap jalan; tidak ada saran grup; pilih grup manual tetap bisa

### B5. Edit semua metadata di halaman detail

#### TES 36 — Mode edit metadata di Detail
Buka catatan apa pun yang punya metadata → kartu "metadata" → tombol **Edit**.
- [ ] Editor lengkap muncul: judul, jenis (chips), jam mulai/selesai (picker jam), tanggal (kalender multi-tanggal), lokasi/orang/organisasi/keywords, ringkasan, action items (deadline = picker tanggal+jam), Kegiatan lain, Transaksi, Waktu alarm
- [ ] Ubah judul + jam mulai → **Simpan metadata** → snackbar "Metadata disimpan", tampilan read-only ikut berubah
- [ ] Ubah sesuatu → **Batal** → perubahan TIDAK tersimpan
- [ ] Ubah tanggal ke besok + nyalakan Waktu alarm → simpan → alarm/pengingat terjadwal ulang (cek notifikasi saat waktunya / Debug Log)

#### TES 37 — Editor Kegiatan lain (extraSchedules)
Buka catatan hasil ekstraksi multi-kegiatan (atau tambah manual):
- [ ] "Tambah kegiatan" → isi judul, jenis, tanggal (kalender), jam mulai (picker), switch Alarm keras → simpan → baris "Kegiatan lain" tampil di read-only & pengingatnya dibuat
- [ ] Hapus kegiatan → simpan → pengingat kegiatan itu ikut hilang

#### TES 38 — Editor Transaksi
- [ ] "Tambah transaksi" → isi item, Keluar/Masuk, nominal, kategori, tanggal → simpan → transaksi muncul di halaman Keuangan
- [ ] "Detail lainnya ▾" → qty/satuan/mata uang/metode/merchant/orang/catatan bisa diisi
- [ ] Nominal diketik "30.000" terbaca 30000; diketik "abc" → field merah, nilai lama tidak berubah
- [ ] Editor yang sama juga muncul di Preview (sebelum simpan catatan baru)

#### TES 39 — Konfirmasi proses ulang
Detail → Edit teks asli → "Simpan & proses ulang".
- [ ] Dialog konfirmasi muncul menyebut metadata manual akan ditimpa; **Batal** = tidak terjadi apa-apa; **Lanjut** = ekstraksi jalan

#### TES 40 — Catatan tanpa metadata (pending)
Buka catatan yang masih pending ekstraksi (offline).
- [ ] Kartu metadata menampilkan "Belum ada metadata — ketuk Edit untuk mengisi manual"; isi manual → simpan → metadata tampil & status pending hilang

---

## Catatan Lingkungan
- Model AI default: `gemini-2.5-flash` (jangan kembali ke `gemini-2.0-flash` — sudah dihapus dari free tier per Juni 2026).
- Build APK via GitHub Actions (`.github/workflows/build.yml`) — tidak ada Java/Gradle lokal.
- Untuk diagnosis AI, gunakan panel Debug Log (Settings → Debug Log).
