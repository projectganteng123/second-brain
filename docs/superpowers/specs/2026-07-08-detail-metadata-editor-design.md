# Edit semua metadata di halaman detail catatan

Tanggal: 2026-07-08 · Status: disetujui user ("langsung buat")

## Tujuan

Semua field metadata bisa diedit di halaman detail catatan (NoteDetailScreen) dengan alat
bantu yang sudah ada (TimePicker, DatePicker kalender, DateTimePicker). Termasuk dua field
yang sebelumnya tidak bisa diedit di mana pun: **Kegiatan lain** (`extraSchedules`) dan
**Transaksi** (`transactions`) — Preview otomatis ikut kebagian karena komponen dibuat bersama.

## Keputusan user

1. Cakupan: benar-benar semua field, termasuk extraSchedules & transactions.
2. Pola edit di Detail: tombol **Edit → Simpan/Batal** (bukan auto-save per ketukan).
3. "Simpan & proses ulang": **dialog konfirmasi** bahwa metadata (termasuk editan manual)
   akan ditimpa hasil AI. Tanpa opsi "simpan teks saja".

## Arsitektur (Pendekatan A — komponen bersama)

- `ui/components/MetadataEditor.kt` (baru, public): pindahan `MetadataEditor` private dari
  PreviewScreen, tanpa perubahan perilaku field lama (judul, jenis chips, jam mulai/selesai,
  tanggal multi, lokasi/orang/organisasi/keywords pisah-koma, ringkasan, action items dengan
  deadline DateTimeField).
- Editor baru di komponen yang sama:
  - **Kegiatan lain** — kartu per kegiatan: judul (teks), jenis (chips meeting/task/event/
    reminder), tanggal (MultiDateField), jam mulai (TimeField), switch alarm keras;
    hapus per kartu + tombol "Tambah kegiatan".
  - **Transaksi** — kartu per transaksi; field inti selalu tampil: item, jenis keluar/masuk,
    nominal (keyboard angka), kategori, tanggal (DateField). Field jarang dipakai dilipat
    "Detail lainnya ▾": qty, satuan, mata uang, metode bayar, merchant, orang, catatan.
    Hapus per kartu + "Tambah transaksi".
- `util/AmountParse.kt` (baru): parser nominal toleran gaya Indonesia ("30.000", "30.000,50",
  "30,5", "Rp 30.000"). Input tak valid → field merah, nilai lama TIDAK ditimpa diam-diam.

## Halaman detail

- Kartu metadata dapat tombol "Edit" (pola sama kartu teks asli). Mode edit: `MetadataEditor`
  + editor waktu alarm (`alarmTimes`, pola dari Preview: switch + DateTimeField multi) +
  tombol **Simpan** / **Batal**.
- Simpan → `NoteDetailViewModel.saveMetadata(meta)` →
  `repo.updateMetadata(noteId, meta, prefs.getAlarmOffsetMinutes())` (pengingat/alarm dibuat
  ulang oleh mekanisme yang sudah ada) → snackbar "Metadata disimpan".
- "Simpan & proses ulang" → AlertDialog konfirmasi dulu.

## Data lama & error

- `extraSchedules`/`transactions` null pada catatan lama → editor mulai kosong (`.orEmpty()`).
- `alarmTimes` dibaca via `alarmTimesEffective()` (fallback field legacy), disimpan ke field
  baru; field legacy di-null-kan saat simpan (pola PreviewScreen).

## Testing

- Unit (Robolectric/JUnit, jalan di CI): round-trip `updateMetadata` dengan extraSchedules &
  transactions termodifikasi; parser nominal (kasus titik ribuan, koma desimal, Rp, invalid).
- Manual di device: baris tes baru di TESTING.md.
