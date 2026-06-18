# Second Brain

Aplikasi Android second brain untuk orang dewasa pekerja — catat ide/jadwal via suara atau teks, ekstrak metadata otomatis dengan Gemini AI, dan dapatkan pengingat.

**Stack:** Kotlin · Jetpack Compose · Room (SQLite) · Gemini API

## Build via GitHub Actions (untuk testing)

Setiap push ke `main` akan otomatis build APK debug. Untuk mengambil hasilnya:

1. Buka tab **Actions** di repository GitHub
2. Pilih run **Build APK** terbaru (yang berhasil / centang hijau)
3. Scroll ke bagian **Artifacts** → unduh `secondbrain-debug-apk`
4. Ekstrak zip → install `app-debug.apk` di HP Android (aktifkan "Install from unknown sources")

Bisa juga trigger manual: tab **Actions** → **Build APK** → **Run workflow**.

## Setup setelah install

1. Buka app → **Pengaturan** → masukkan **Gemini API Key**
   (gratis di [Google AI Studio](https://aistudio.google.com/app/apikey))
2. Mulai mencatat lewat tombol **+**

## Build lokal (opsional)

Buka folder ini di Android Studio (Giraffe+), tunggu Gradle sync, lalu Run.
Minimal Android 8.0 (API 26).
