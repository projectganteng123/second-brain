# Desain: Grup Catatan

**Tanggal:** 2026-07-08
**Status:** Disetujui user (brainstorming selesai)

## Ringkasan

Catatan dapat dihubungkan lewat **grup bernama** (mis. "Renovasi Rumah"). Satu catatan
boleh masuk banyak grup (many-to-many). Keanggotaan diisi **manual oleh user + saran AI**
saat ekstraksi: AI mengusulkan grup yang cocok, user yang memutuskan. AI boleh mengusulkan
maksimal 1 nama grup baru, tampil tidak tercentang — grup baru hanya terbentuk jika user
mencentangnya.

Di luar cakupan (YAGNI, bisa ditambah nanti tanpa bongkar skema):

- Link langsung catatan-ke-catatan (backlink ala Obsidian).
- Grup bersarang / sub-grup (cukup tambah kolom `parentId` kelak).
- Penempatan otomatis penuh oleh AI tanpa konfirmasi.

## 1. Skema database

Migrasi Room **3 → 4**. Tabel `notes` TIDAK berubah. Dua tabel baru:

### `groups` — entity `GroupEntity.kt`

| Kolom        | Tipe    | Keterangan                                        |
| ------------ | ------- | ------------------------------------------------- |
| `id`         | Long PK | autoGenerate                                      |
| `name`       | String  | unik case-insensitive (unique index `COLLATE NOCASE`) |
| `color`      | String? | warna chip di UI, opsional                        |
| `createdAt`  | Long    | epoch millis                                      |
| `isArchived` | Boolean | arsip grup tanpa menghapus riwayat keanggotaan    |

### `note_group_cross_ref` — entity `NoteGroupCrossRef.kt`

| Kolom     | Tipe | Keterangan                          |
| --------- | ---- | ----------------------------------- |
| `noteId`  | Long | composite PK bersama `groupId`      |
| `groupId` | Long | composite PK bersama `noteId`       |
| `addedAt` | Long | epoch millis                        |

- FK `noteId → notes.id` **ON DELETE CASCADE**.
- FK `groupId → groups.id` **ON DELETE CASCADE**.
- Index tambahan pada `groupId` (query "isi grup X" cepat; index PK sudah meng-cover `noteId`).

### `GroupDao`

- CRUD grup (insert, rename, set warna, arsip, hapus).
- Tambah/hapus keanggotaan (insert/delete cross-ref).
- `Flow<List<NoteEntity>>` catatan dalam satu grup; `Flow<List<GroupEntity>>` grup milik
  satu catatan.
- Daftar grup aktif + jumlah catatan per grup (untuk layar Grup).
- Cari grup berdasarkan nama, case-insensitive + trim (untuk resolve saran AI).

## 2. Saran AI (menumpang prompt Universal — tanpa panggilan AI tambahan)

- Placeholder baru **`{groups}`** di `PromptTemplates`, diisi array JSON nama grup aktif
  saat `AIService.forExtraction` membangun prompt: maksimal **50 grup**, terbaru dulu.
  Belum ada grup → `[]`.
- Prompt Universal (`DEFAULT_UNIVERSAL`) mendapat blok `GRUP YANG SUDAH ADA: {groups}`
  dan field output baru:

  ```json
  "suggestedGroups": ["nama grup"]
  ```

  Aturan di prompt: utamakan memilih dari GRUP YANG SUDAH ADA; boleh mengusulkan
  **maksimal 1 nama baru** hanya jika catatan jelas bertema berulang; `[]` jika tidak
  relevan.
- `ExtractionParser` mem-parse ke field baru `Metadata.suggestedGroups: List<String>?`
  — nullable mengikuti pola `transactions`/`extraSchedules` agar `metadataJson` catatan
  lama aman dibaca Gson.
- **Prompt custom tanpa `{groups}` aman**: placeholder tak ditemukan → AI tidak diberi
  daftar grup → `suggestedGroups` kosong/absen → fitur manual tetap berfungsi penuh.

## 3. Alur data

### Jalur Preview (capture normal)

1. Ekstraksi menghasilkan `Metadata.suggestedGroups`.
2. Preview menampilkan baris chip grup:
   - Saran yang cocok grup existing (case-insensitive + trim) → chip **tercentang**.
   - Usulan nama baru → chip `+ Nama (baru)` **tidak tercentang**.
   - User bisa toggle semua chip dan menambah grup lain lewat picker.
3. Saat Simpan: nama terpilih di-resolve ke `groups.id` (match case-insensitive + trim;
   buat grup baru bila chip "(baru)" dicentang) → tulis ke `note_group_cross_ref` →
   `suggestedGroups` dikosongkan dari metadata (sudah dikonsumsi).

### Jalur Pending (background, `PendingProcessor`, tanpa Preview)

- `suggestedGroups` tersimpan diam di `metadataJson`. **Tidak ada yang diterapkan
  otomatis.**
- Saat catatan dibuka di Detail: baris "Saran grup:" dengan chip yang bisa di-tap untuk
  **terima** (resolve → junction table, hapus dari saran) atau **tolak** (hapus dari
  saran). Saran habis → baris hilang.

## 4. UI

- **Komponen chip grup bersama** (gaya `GlassComponents`), dipakai Preview dan Detail.
- **Preview** (`PreviewScreen`): baris chip saran + picker tambah grup.
- **Detail** (`NoteDetailScreen`): daftar grup catatan (tap → buka isi grup), tambah/lepas
  grup kapan saja, plus baris saran untuk catatan jalur pending.
- **Layar Grup baru**: daftar grup aktif + jumlah catatan; tap → daftar catatan dalam grup
  (pakai `NoteCard` existing); buat/rename/ubah warna/arsip/hapus grup dari sini.
  Terdaftar di `NavGraph`; penempatan pintu masuk (halaman `HomePagerScreen` atau menu)
  diputuskan saat implementasi setelah melihat struktur pager.

## 5. Edge case & penanganan error

- **Rename grup** → update 1 baris `groups.name`; keanggotaan tak tersentuh. Tolak rename
  yang bentrok nama existing (case-insensitive).
- **Hapus grup** → CASCADE membersihkan cross-ref; catatan TIDAK terhapus. Dialog
  konfirmasi menyebut jumlah catatan terdampak.
- **Hapus catatan** → cross-ref ikut terhapus (CASCADE), tak ada baris yatim.
- **Saran menunjuk grup yang sudah dihapus/diarsip** → resolve gagal diam-diam, chip tidak
  ditampilkan.
- **Duplikasi nama karena kapitalisasi/spasi** ("renovasi rumah" vs "Renovasi Rumah ") →
  dicegah dua lapis: unique index `COLLATE NOCASE` di DB + normalisasi trim saat resolve.

## 6. Pengujian

- **Unit**: parsing `suggestedGroups` (ada, kosong, absen di JSON catatan lama); resolve
  nama → id (case-insensitive, trim, buat-baru); merge `ExtractionParser` tetap benar.
- **DAO (Room in-memory)**: CASCADE dua arah; query catatan-per-grup dan grup-per-catatan;
  unique index nama; jumlah catatan per grup.
- **Migrasi**: test `MigrationTestHelper` 3 → 4 — data notes/reminders lama utuh, tabel
  baru terbentuk.
