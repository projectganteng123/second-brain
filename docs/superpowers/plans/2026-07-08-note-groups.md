# Grup Catatan — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Catatan bisa dimasukkan ke grup bernama (many-to-many), diisi manual + saran AI saat ekstraksi, dengan layar kelola grup.

**Architecture:** Dua tabel Room baru (`groups` + junction `note_group_cross_ref`, migrasi 3→4) dipisah tegas dari *saran* AI yang hanya hidup di `Metadata.suggestedGroups` (JSON, tanpa migrasi). Saran diproduksi prompt Universal via placeholder `{groups}`; keanggotaan nyata hanya ditulis saat user konfirmasi (Preview) atau menerima saran (Detail, jalur pending).

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.6.1 (KSP), Gson, JUnit4 + Robolectric (test JVM di CI).

**Spec:** `docs/superpowers/specs/2026-07-08-note-groups-design.md`

## Global Constraints

- **TIDAK ADA Java/Gradle di mesin lokal.** Verifikasi build & test HANYA via GitHub Actions setelah push. Push ke `origin main` dilakukan USER sendiri (jangan `git push`). Karena itu langkah "run test" klasik TDD diganti: tulis test + implementasi per task → commit → checkpoint CI di akhir (Task 8).
- Commit lokal per task dengan pesan berformat `feat: ...` / `test: ...`, diakhiri baris `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Semua string UI Bahasa Indonesia; komentar kode Bahasa Indonesia (ikuti gaya file yang ada).
- Room database version: 3 → **4**. Nama DB `secondbrain.db` tidak berubah.
- Maks **50** nama grup disuntikkan ke prompt (`activeGroupNames(limit = 50)`).
- AI boleh usulkan maks 1 grup baru; chip usulan baru TIDAK tercentang di Preview.
- Pencocokan nama grup SELALU case-insensitive + trim, dua lapis: unique index `COLLATE NOCASE` di DB + normalisasi di repository.
- Test migrasi `MigrationTestHelper` TIDAK dibuat (skema v1–3 tidak pernah diekspor; `exportSchema` semula false). Sebagai gantinya Task 1 mengaktifkan ekspor skema mulai v4, dan migrasi 3→4 diverifikasi di device (Task 8). Ini deviasi sadar dari spec bagian 6.
- JANGAN menambah validasi placeholder `{groups}` di Settings — prompt custom tanpa `{groups}` memang sah (fitur saran mati, manual tetap jalan).

---

### Task 1: Infrastruktur test (JUnit + Robolectric) + CI

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `.github/workflows/build.yml`
- Create: `app/src/test/java/com/secondbrain/app/SanityTest.kt`

**Interfaces:**
- Consumes: —
- Produces: source set `app/src/test/`; task CI `testDebugUnitTest`; KSP arg `room.schemaLocation` (folder `app/schemas/` mulai terisi saat DB naik ke v4 di Task 2).

- [ ] **Step 1: Tambah versi & library test di version catalog**

Di `gradle/libs.versions.toml`, blok `[versions]` — tambah setelah baris `coroutines = "1.9.0"`:

```toml
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestCore = "1.6.1"
```

Blok `[libraries]` — tambah setelah baris `androidx-ui-tooling = ...`:

```toml
junit = { group = "junit", name = "junit", version.ref = "junit" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
androidx-test-core = { group = "androidx.test", name = "core-ktx", version.ref = "androidxTestCore" }
```

- [ ] **Step 2: Tambah dependensi test + opsi unit test + ekspor skema Room di `app/build.gradle.kts`**

Di dalam blok `android { ... }`, tambah setelah blok `buildFeatures { compose = true }`:

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

Setelah blok `android { ... }` (top-level, sebelum `dependencies`):

```kotlin
// Ekspor skema Room ke app/schemas mulai v4 — bekal test migrasi versi berikutnya.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

Di blok `dependencies { ... }`, tambah di akhir:

```kotlin
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
```

- [ ] **Step 3: Tulis sanity test**

Create `app/src/test/java/com/secondbrain/app/SanityTest.kt`:

```kotlin
package com.secondbrain.app

import org.junit.Assert.assertTrue
import org.junit.Test

/** Memastikan infrastruktur unit test berjalan di CI. */
class SanityTest {
    @Test
    fun testInfraBerjalan() {
        assertTrue(true)
    }
}
```

- [ ] **Step 4: Tambah step test di CI**

Di `.github/workflows/build.yml`, sisipkan step baru SEBELUM step `- name: Build debug APK`:

```yaml
      - name: Run unit tests
        run: gradle testDebugUnitTest --no-daemon --stacktrace
```

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts .github/workflows/build.yml app/src/test
git commit -m "test: infrastruktur unit test (JUnit+Robolectric) + step test di CI"
```

---

### Task 2: Skema DB v4 — GroupEntity, NoteGroupCrossRef, GroupDao, migrasi 3→4

**Files:**
- Create: `app/src/main/java/com/secondbrain/app/data/model/GroupEntity.kt`
- Create: `app/src/main/java/com/secondbrain/app/data/model/NoteGroupCrossRef.kt`
- Create: `app/src/main/java/com/secondbrain/app/data/database/GroupDao.kt`
- Modify: `app/src/main/java/com/secondbrain/app/data/database/AppDatabase.kt`
- Test: `app/src/test/java/com/secondbrain/app/data/database/GroupDaoTest.kt`

**Interfaces:**
- Consumes: `NoteEntity` (FK `notes.id`).
- Produces (dipakai Task 4):
  - `GroupEntity(id: Long, name: String, color: String?, createdAt: Long, isArchived: Boolean)`
  - `NoteGroupCrossRef(noteId: Long, groupId: Long, addedAt: Long)`
  - `GroupWithCount(group: GroupEntity, noteCount: Int)`
  - `GroupDao` — lihat kode Step 3 (nama fungsi persis).
  - `AppDatabase.groupDao(): GroupDao`, DB version 4.

- [ ] **Step 1: Buat `GroupEntity`**

Create `app/src/main/java/com/secondbrain/app/data/model/GroupEntity.kt`:

```kotlin
package com.secondbrain.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Grup/proyek bernama. Nama unik case-insensitive (COLLATE NOCASE + unique index). */
@Entity(
    tableName = "groups",
    indices = [Index(value = ["name"], unique = true)]
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** Warna chip di UI (hex "#RRGGBB"); null = warna default tema. */
    val color: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Arsip grup menyembunyikannya tanpa menghapus riwayat keanggotaan. */
    val isArchived: Boolean = false
)
```

- [ ] **Step 2: Buat `NoteGroupCrossRef`**

Create `app/src/main/java/com/secondbrain/app/data/model/NoteGroupCrossRef.kt`:

```kotlin
package com.secondbrain.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Keanggotaan catatan di grup (many-to-many). Hapus catatan/grup → baris ikut terhapus. */
@Entity(
    tableName = "note_group_cross_ref",
    primaryKeys = ["noteId", "groupId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("groupId")]
)
data class NoteGroupCrossRef(
    val noteId: Long,
    val groupId: Long,
    val addedAt: Long = System.currentTimeMillis()
)
```

- [ ] **Step 3: Buat `GroupDao` + `GroupWithCount`**

Create `app/src/main/java/com/secondbrain/app/data/database/GroupDao.kt`:

```kotlin
package com.secondbrain.app.data.database

import androidx.room.*
import com.secondbrain.app.data.model.GroupEntity
import com.secondbrain.app.data.model.NoteEntity
import com.secondbrain.app.data.model.NoteGroupCrossRef
import kotlinx.coroutines.flow.Flow

/** Grup + jumlah catatan aktif di dalamnya (untuk layar daftar Grup). */
data class GroupWithCount(
    @Embedded val group: GroupEntity,
    val noteCount: Int
)

@Dao
interface GroupDao {

    // ---- Grup ----

    @Insert
    suspend fun insert(group: GroupEntity): Long

    @Update
    suspend fun update(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun getById(id: Long): GroupEntity?

    /** Cari nama persis (COLLATE NOCASE di kolom) — termasuk grup terarsip,
     *  karena unique index juga mencakup baris arsip. */
    @Query("SELECT * FROM groups WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE isArchived = 0 ORDER BY createdAt DESC")
    fun activeGroups(): Flow<List<GroupEntity>>

    /** Nama grup aktif untuk disuntikkan ke prompt (terbaru dulu). */
    @Query("SELECT name FROM groups WHERE isArchived = 0 ORDER BY createdAt DESC LIMIT :limit")
    suspend fun activeGroupNames(limit: Int): List<String>

    @Query("""
        SELECT groups.*,
               (SELECT COUNT(*) FROM note_group_cross_ref c
                INNER JOIN notes n ON n.id = c.noteId AND n.isArchived = 0
                WHERE c.groupId = groups.id) AS noteCount
        FROM groups WHERE isArchived = 0 ORDER BY createdAt DESC
    """)
    fun activeGroupsWithCount(): Flow<List<GroupWithCount>>

    // ---- Keanggotaan ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCrossRef(ref: NoteGroupCrossRef)

    @Query("DELETE FROM note_group_cross_ref WHERE noteId = :noteId AND groupId = :groupId")
    suspend fun removeCrossRef(noteId: Long, groupId: Long)

    @Query("""
        SELECT notes.* FROM notes
        INNER JOIN note_group_cross_ref c ON notes.id = c.noteId
        WHERE c.groupId = :groupId AND notes.isArchived = 0
        ORDER BY notes.createdAt DESC
    """)
    fun notesInGroup(groupId: Long): Flow<List<NoteEntity>>

    @Query("""
        SELECT groups.* FROM groups
        INNER JOIN note_group_cross_ref c ON groups.id = c.groupId
        WHERE c.noteId = :noteId AND groups.isArchived = 0
        ORDER BY groups.name
    """)
    fun groupsOfNote(noteId: Long): Flow<List<GroupEntity>>

    /** Jumlah keanggotaan (untuk dialog konfirmasi hapus grup). */
    @Query("SELECT COUNT(*) FROM note_group_cross_ref WHERE groupId = :groupId")
    suspend fun memberCount(groupId: Long): Int
}
```

- [ ] **Step 4: Naikkan `AppDatabase` ke v4 + migrasi**

Modify `app/src/main/java/com/secondbrain/app/data/database/AppDatabase.kt`. Tambah import:

```kotlin
import com.secondbrain.app.data.model.GroupEntity
import com.secondbrain.app.data.model.NoteGroupCrossRef
```

Ubah anotasi + abstract fun (perhatikan `exportSchema = true` — folder `app/schemas` mulai terisi berkat KSP arg dari Task 1):

```kotlin
@Database(
    entities = [NoteEntity::class, ReminderEntity::class, GroupEntity::class, NoteGroupCrossRef::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun groupDao(): GroupDao
```

Tambah migrasi (setelah `MIGRATION_2_3`) — SQL HARUS persis begini agar cocok dengan skema yang diharapkan Room:

```kotlin
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `groups` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL COLLATE NOCASE,
                        `color` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_groups_name` ON `groups` (`name`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `note_group_cross_ref` (
                        `noteId` INTEGER NOT NULL,
                        `groupId` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`noteId`, `groupId`),
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`groupId`) REFERENCES `groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_group_cross_ref_groupId` ON `note_group_cross_ref` (`groupId`)")
            }
        }
```

Dan daftarkan: ubah `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` menjadi `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.

- [ ] **Step 5: Tulis test DAO (Robolectric, in-memory Room)**

Create `app/src/test/java/com/secondbrain/app/data/database/GroupDaoTest.kt`:

```kotlin
package com.secondbrain.app.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.secondbrain.app.data.model.GroupEntity
import com.secondbrain.app.data.model.NoteEntity
import com.secondbrain.app.data.model.NoteGroupCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var groupDao: GroupDao
    private lateinit var noteDao: NoteDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        groupDao = db.groupDao()
        noteDao = db.noteDao()
    }

    @After
    fun teardown() { db.close() }

    private fun note(text: String = "catatan") = NoteEntity(rawText = text)

    @Test
    fun namaGrupUnikCaseInsensitive() = runBlocking {
        groupDao.insert(GroupEntity(name = "Renovasi Rumah"))
        val kedua = runCatching { groupDao.insert(GroupEntity(name = "renovasi rumah")) }
        assertTrue("insert nama kembar (beda kapital) harus gagal", kedua.isFailure)
    }

    @Test
    fun findByNameCaseInsensitive() = runBlocking {
        groupDao.insert(GroupEntity(name = "Keuangan Juli"))
        assertNotNull(groupDao.findByName("keuangan juli"))
        assertNull(groupDao.findByName("tidak ada"))
    }

    @Test
    fun hapusCatatanMembersihkanKeanggotaan() = runBlocking {
        val noteId = noteDao.insert(note())
        val groupId = groupDao.insert(GroupEntity(name = "G"))
        groupDao.addCrossRef(NoteGroupCrossRef(noteId, groupId))
        assertEquals(1, groupDao.memberCount(groupId))

        noteDao.delete(noteDao.getById(noteId)!!)
        assertEquals("CASCADE: cross-ref ikut terhapus", 0, groupDao.memberCount(groupId))
    }

    @Test
    fun hapusGrupMembersihkanKeanggotaanTanpaHapusCatatan() = runBlocking {
        val noteId = noteDao.insert(note())
        val groupId = groupDao.insert(GroupEntity(name = "G"))
        groupDao.addCrossRef(NoteGroupCrossRef(noteId, groupId))

        groupDao.delete(groupId)
        assertEquals(0, groupDao.memberCount(groupId))
        assertNotNull("catatan TIDAK ikut terhapus", noteDao.getById(noteId))
    }

    @Test
    fun queryDuaArah() = runBlocking {
        val n1 = noteDao.insert(note("a")); val n2 = noteDao.insert(note("b"))
        val g1 = groupDao.insert(GroupEntity(name = "G1")); val g2 = groupDao.insert(GroupEntity(name = "G2"))
        groupDao.addCrossRef(NoteGroupCrossRef(n1, g1))
        groupDao.addCrossRef(NoteGroupCrossRef(n2, g1))
        groupDao.addCrossRef(NoteGroupCrossRef(n1, g2))

        assertEquals(2, groupDao.notesInGroup(g1).first().size)
        assertEquals(listOf("G1", "G2"), groupDao.groupsOfNote(n1).first().map { it.name })
    }

    @Test
    fun addCrossRefDuaKaliTidakError() = runBlocking {
        val n = noteDao.insert(note()); val g = groupDao.insert(GroupEntity(name = "G"))
        groupDao.addCrossRef(NoteGroupCrossRef(n, g))
        groupDao.addCrossRef(NoteGroupCrossRef(n, g))   // OnConflictStrategy.IGNORE
        assertEquals(1, groupDao.memberCount(g))
    }

    @Test
    fun hitungCatatanPerGrupMengabaikanArsip() = runBlocking {
        val n1 = noteDao.insert(note("aktif")); val n2 = noteDao.insert(note("arsip"))
        val g = groupDao.insert(GroupEntity(name = "G"))
        groupDao.addCrossRef(NoteGroupCrossRef(n1, g))
        groupDao.addCrossRef(NoteGroupCrossRef(n2, g))
        noteDao.setArchived(n2, true)

        val withCount = groupDao.activeGroupsWithCount().first().single()
        assertEquals(1, withCount.noteCount)
        assertEquals(1, groupDao.notesInGroup(g).first().size)
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/secondbrain/app/data app/src/test
git commit -m "feat: skema grup catatan (groups + note_group_cross_ref, Room v4) + test DAO"
```

(Catatan: folder `app/schemas/` baru terisi saat build di CI — tidak ada yang perlu di-commit secara lokal untuk skema.)

---

### Task 3: Lapisan AI — `Metadata.suggestedGroups`, prompt `{groups}`, parser

**Files:**
- Modify: `app/src/main/java/com/secondbrain/app/data/model/Metadata.kt`
- Modify: `app/src/main/java/com/secondbrain/app/ai/PromptTemplates.kt`
- Modify: `app/src/main/java/com/secondbrain/app/ai/ExtractionParser.kt`
- Modify: `app/src/main/java/com/secondbrain/app/ai/AIService.kt`
- Test: `app/src/test/java/com/secondbrain/app/ai/SuggestedGroupsParsingTest.kt`

**Interfaces:**
- Consumes: —
- Produces (dipakai Task 4–6):
  - `Metadata.suggestedGroups: List<String>?` (nullable; JSON lama aman)
  - `PromptTemplates.PLACEHOLDER_GROUPS = "{groups}"`; `fill(template, now, note, groups: List<String> = emptyList())`
  - `AIService.extractMetadata(rawText: String, now: String, groupNames: List<String> = emptyList())` — default kosong agar pemanggil lama tetap kompilasi sampai Task 4 memperbaruinya.

- [ ] **Step 1: Tambah field di `Metadata`**

Di `Metadata.kt`, tambah field terakhir di data class `Metadata` (setelah `val suggestAlarm: Boolean = false`, tambahkan koma pada baris itu):

```kotlin
    /** Saran grup dari AI (nama grup; belum tentu ada di DB). Nullable agar metadataJson
     *  catatan LAMA aman dibaca Gson. Dikosongkan setelah dikonsumsi (Preview/Detail). */
    val suggestedGroups: List<String>? = null
```

- [ ] **Step 2: Placeholder `{groups}` + isi prompt Universal**

Di `PromptTemplates.kt`:

(a) Tambah konstanta setelah `PLACEHOLDER_NOTE`:

```kotlin
    const val PLACEHOLDER_GROUPS = "{groups}"
```

(b) Di `DEFAULT_UNIVERSAL`, setelah baris `(digunakan hanya untuk mengubah deadline relatif pada action menjadi tanggal & jam absolut)` dan sebelum `Catatan:`, sisipkan:

```
GRUP YANG SUDAH ADA: {groups}
```

(c) Masih di `DEFAULT_UNIVERSAL`, di dalam "Struktur JSON" tambahkan field setelah `"keywords": ["kata penting"],`:

```
  "suggestedGroups": ["nama grup"],
```

(d) Di bagian "Aturan:" `DEFAULT_UNIVERSAL`, tambah setelah baris `- keywords berisi kata penting, ...`:

```
- suggestedGroups: pilih dari GRUP YANG SUDAH ADA yang paling sesuai isi catatan (boleh lebih dari satu).
- suggestedGroups boleh berisi MAKSIMAL 1 nama grup BARU (2-4 kata) hanya jika catatan jelas bagian
  dari proyek/tema berulang dan tidak ada grup yang cocok di daftar.
- Jika tidak ada grup yang relevan, suggestedGroups = [].
```

(e) Ubah `fill` menjadi:

```kotlin
    fun fill(template: String, now: String, note: String, groups: List<String> = emptyList()): String =
        template
            .replace(PLACEHOLDER_NOW, now)
            .replace(PLACEHOLDER_NOTE, note)
            .replace(PLACEHOLDER_GROUPS, com.secondbrain.app.data.GsonProvider.gson.toJson(groups))
```

(Template Keuangan/Acara tidak memuat `{groups}` — replace no-op, aman. Prompt custom user tanpa `{groups}` juga aman.)

- [ ] **Step 3: Parse `suggestedGroups` di `ExtractionParser`**

(a) Di `UniversalDto`, tambah field setelah `val keywords: List<String>? = null,`:

```kotlin
        val suggestedGroups: List<String>? = null,
```

(b) Di `merge()`, pada konstruksi `Metadata(...)` tambah argumen setelah `suggestAlarm = main?.useAlarm == true` (tambahkan koma pada baris itu):

```kotlin
            suggestedGroups = u.suggestedGroups.orEmpty()
                .map { it.trim() }.filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .takeIf { it.isNotEmpty() }
```

- [ ] **Step 4: Teruskan nama grup di `AIService`**

Di `AIService.kt`, ubah tanda tangan `extractMetadata`:

```kotlin
    suspend fun extractMetadata(
        rawText: String,
        now: String,
        groupNames: List<String> = emptyList()
    ): Result<Metadata> = runCatching {
```

dan di dalam `launchPrompt`, ubah pemanggilan fill:

```kotlin
                        it.generateJson(PromptTemplates.fill(template, now, rawText, groupNames))
```

- [ ] **Step 5: Tulis test parsing**

Create `app/src/test/java/com/secondbrain/app/ai/SuggestedGroupsParsingTest.kt`:

```kotlin
package com.secondbrain.app.ai

import com.secondbrain.app.data.GsonProvider
import com.secondbrain.app.data.model.Metadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuggestedGroupsParsingTest {

    private val financeKosong = """{"transactions":[]}"""
    private val scheduleKosong = """{"schedules":[]}"""

    @Test
    fun parseSuggestedGroups_trimDanDedupCaseInsensitive() {
        val universal = """
            {"title":"t","type":"note","summary":"s",
             "suggestedGroups":[" Renovasi Rumah ", "renovasi rumah", "Ide Bisnis", ""]}
        """.trimIndent()
        val meta = ExtractionParser.merge(universal, financeKosong, scheduleKosong)
        assertEquals(listOf("Renovasi Rumah", "Ide Bisnis"), meta.suggestedGroups)
    }

    @Test
    fun fieldAbsen_jadiNull() {
        val universal = """{"title":"t","type":"note","summary":"s"}"""
        val meta = ExtractionParser.merge(universal, financeKosong, scheduleKosong)
        assertNull(meta.suggestedGroups)
    }

    @Test
    fun arrayKosong_jadiNull() {
        val universal = """{"title":"t","type":"note","summary":"s","suggestedGroups":[]}"""
        val meta = ExtractionParser.merge(universal, financeKosong, scheduleKosong)
        assertNull(meta.suggestedGroups)
    }

    @Test
    fun metadataJsonLama_tanpaField_amanDibaca() {
        // metadataJson catatan lama (pra-fitur grup) tidak memuat suggestedGroups
        val lama = """{"title":"lama","type":"NOTE","summary":"","keywords":[]}"""
        val meta = GsonProvider.gson.fromJson(lama, Metadata::class.java)
        assertNull(meta.suggestedGroups)
    }

    @Test
    fun fillMenyuntikkanDaftarGrup() {
        val hasil = PromptTemplates.fill(
            "DAFTAR: {groups} CATATAN: {note}", "now", "isi",
            listOf("Renovasi Rumah", "Ide Bisnis")
        )
        assertTrue(hasil.contains("""["Renovasi Rumah","Ide Bisnis"]"""))
        assertFalse(hasil.contains("{groups}"))
    }

    @Test
    fun fillTanpaGrup_placeholderJadiArrayKosong() {
        val hasil = PromptTemplates.fill("DAFTAR: {groups}", "now", "isi")
        assertTrue(hasil.contains("DAFTAR: []"))
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/secondbrain/app/data/model/Metadata.kt app/src/main/java/com/secondbrain/app/ai app/src/test
git commit -m "feat: saran grup dari AI — Metadata.suggestedGroups + placeholder {groups} di prompt Universal"
```

---

### Task 4: Repository — resolve nama→grup, keanggotaan, wiring, pemanggil ekstraksi

**Files:**
- Modify: `app/src/main/java/com/secondbrain/app/data/repository/NoteRepository.kt`
- Modify: `app/src/main/java/com/secondbrain/app/SecondBrainApp.kt`
- Modify: `app/src/main/java/com/secondbrain/app/viewmodel/InputViewModel.kt` (hanya pemanggilan extractMetadata)
- Modify: `app/src/main/java/com/secondbrain/app/viewmodel/NoteDetailViewModel.kt` (hanya pemanggilan extractMetadata)
- Modify: `app/src/main/java/com/secondbrain/app/util/PendingProcessor.kt`
- Test: `app/src/test/java/com/secondbrain/app/data/repository/NoteRepositoryGroupTest.kt`

**Interfaces:**
- Consumes: `GroupDao`, `GroupEntity`, `NoteGroupCrossRef`, `GroupWithCount` (Task 2); `Metadata.suggestedGroups` (Task 3).
- Produces (dipakai Task 5–7):
  - `NoteRepository(noteDao, reminderDao, groupDao, gson = ..., appContext = ...)` — parameter baru ke-3.
  - `fun activeGroups(): Flow<List<GroupEntity>>`
  - `fun activeGroupsWithCount(): Flow<List<GroupWithCount>>`
  - `fun notesInGroup(groupId: Long): Flow<List<NoteEntity>>`
  - `fun groupsOfNote(noteId: Long): Flow<List<GroupEntity>>`
  - `suspend fun activeGroupNames(limit: Int = 50): List<String>`
  - `suspend fun getGroup(id: Long): GroupEntity?`
  - `suspend fun resolveOrCreateGroup(name: String): GroupEntity?`
  - `suspend fun assignGroups(noteId: Long, names: List<String>)`
  - `suspend fun addNoteToGroup(noteId: Long, groupId: Long)` / `removeNoteFromGroup(noteId, groupId)`
  - `suspend fun renameGroup(id: Long, newName: String): Boolean` (false = bentrok/invalid)
  - `suspend fun setGroupArchived(id: Long, archived: Boolean)` / `deleteGroup(id: Long)` / `groupMemberCount(groupId: Long): Int`
  - `suspend fun consumeGroupSuggestion(noteId: Long, name: String, accept: Boolean)`
  - `save(..., groupNames: List<String> = emptyList())` — parameter baru terakhir.

- [ ] **Step 1: Tambah `groupDao` ke konstruktor + wiring App**

Di `NoteRepository.kt`, ubah konstruktor:

```kotlin
class NoteRepository(
    private val noteDao: NoteDao,
    private val reminderDao: ReminderDao,
    private val groupDao: com.secondbrain.app.data.database.GroupDao,
    private val gson: Gson = GsonProvider.gson,
    /** Untuk mencabut alarm di AlarmManager LANGSUNG saat pengingat dihapus (nullable untuk tes). */
    private val appContext: android.content.Context? = null
) {
```

Di `SecondBrainApp.kt`, ubah pembuatan repository:

```kotlin
    val repository by lazy {
        NoteRepository(database.noteDao(), database.reminderDao(), database.groupDao(), appContext = this)
    }
```

- [ ] **Step 2: Tambah metode grup di `NoteRepository`**

Tambah blok berikut di `NoteRepository` (setelah fungsi `getAllActive/getArchived/search`, sebelum `save`). Tambahkan juga import `com.secondbrain.app.data.database.GroupWithCount`:

```kotlin
    // ---- Grup ----

    fun activeGroups(): Flow<List<GroupEntity>> = groupDao.activeGroups()
    fun activeGroupsWithCount(): Flow<List<GroupWithCount>> = groupDao.activeGroupsWithCount()
    fun notesInGroup(groupId: Long): Flow<List<NoteEntity>> = groupDao.notesInGroup(groupId)
    fun groupsOfNote(noteId: Long): Flow<List<GroupEntity>> = groupDao.groupsOfNote(noteId)
    suspend fun activeGroupNames(limit: Int = 50): List<String> = groupDao.activeGroupNames(limit)
    suspend fun getGroup(id: Long): GroupEntity? = groupDao.getById(id)
    suspend fun groupMemberCount(groupId: Long): Int = groupDao.memberCount(groupId)

    /** Cari grup by nama (case-insensitive + trim). Belum ada → buat; terarsip → hidupkan
     *  lagi (nama unik mencakup baris arsip, jadi tidak boleh dibuat kembar). */
    suspend fun resolveOrCreateGroup(name: String): GroupEntity? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        groupDao.findByName(clean)?.let { existing ->
            if (!existing.isArchived) return existing
            val revived = existing.copy(isArchived = false)
            groupDao.update(revived)
            return revived
        }
        val id = groupDao.insert(GroupEntity(name = clean))
        DebugLog.log("DB ✓ grup baru", "\"$clean\" (id=$id)")
        return groupDao.getById(id)
    }

    /** Resolve tiap nama lalu tulis keanggotaan (duplikat diabaikan oleh IGNORE). */
    suspend fun assignGroups(noteId: Long, names: List<String>) {
        for (n in names) {
            val g = resolveOrCreateGroup(n) ?: continue
            groupDao.addCrossRef(NoteGroupCrossRef(noteId = noteId, groupId = g.id))
        }
    }

    suspend fun addNoteToGroup(noteId: Long, groupId: Long) =
        groupDao.addCrossRef(NoteGroupCrossRef(noteId = noteId, groupId = groupId))

    suspend fun removeNoteFromGroup(noteId: Long, groupId: Long) =
        groupDao.removeCrossRef(noteId, groupId)

    /** Ganti nama grup. false bila nama kosong atau bentrok dengan grup lain. */
    suspend fun renameGroup(id: Long, newName: String): Boolean {
        val clean = newName.trim()
        if (clean.isEmpty()) return false
        val bentrok = groupDao.findByName(clean)
        if (bentrok != null && bentrok.id != id) return false
        val g = groupDao.getById(id) ?: return false
        groupDao.update(g.copy(name = clean))
        return true
    }

    suspend fun setGroupArchived(id: Long, archived: Boolean) {
        val g = groupDao.getById(id) ?: return
        groupDao.update(g.copy(isArchived = archived))
    }

    suspend fun deleteGroup(id: Long) = groupDao.delete(id)

    /** Terima/tolak SATU saran grup pada catatan (jalur pending di Detail).
     *  accept=true → tulis keanggotaan; dua-duanya menghapus nama itu dari saran. */
    suspend fun consumeGroupSuggestion(noteId: Long, name: String, accept: Boolean) {
        val note = noteDao.getById(noteId) ?: return
        val meta = metadataFrom(note) ?: return
        if (accept) {
            resolveOrCreateGroup(name)?.let {
                groupDao.addCrossRef(NoteGroupCrossRef(noteId = noteId, groupId = it.id))
            }
        }
        val sisa = meta.suggestedGroups.orEmpty()
            .filterNot { it.trim().equals(name.trim(), ignoreCase = true) }
        noteDao.update(note.copy(
            metadataJson = gson.toJson(meta.copy(suggestedGroups = sisa.takeIf { it.isNotEmpty() })),
            updatedAt = System.currentTimeMillis()
        ))
    }
```

- [ ] **Step 3: `save()` menerima nama grup**

Di `NoteRepository.save`, tambah parameter terakhir:

```kotlin
        attachments: List<Attachment> = emptyList(),
        groupNames: List<String> = emptyList()
```

dan setelah baris `generateReminders(id, metadata, alarmOffsetMinutes, useAlarm)` tambah:

```kotlin
        if (groupNames.isNotEmpty()) assignGroups(id, groupNames)
```

- [ ] **Step 4: Pemanggil ekstraksi menyertakan daftar grup**

(a) `InputViewModel.processWithAI` — ubah baris `service.extractMetadata(text, now)` menjadi:

```kotlin
            service.extractMetadata(text, now, repo.activeGroupNames())
```

(b) `NoteDetailViewModel.reExtract` — ubah baris `val result = service.extractMetadata(newRawText, now)` menjadi:

```kotlin
                val result = service.extractMetadata(newRawText, now, repo.activeGroupNames())
```

(c) `PendingProcessor.processAll` — ubah baris `service.extractMetadata(note.rawText, now)` menjadi:

```kotlin
            service.extractMetadata(note.rawText, now, repo.activeGroupNames())
```

`PendingProcessor` menyimpan hasil via `updateMetadata` yang menulis `metadataJson` apa adanya → `suggestedGroups` ikut tersimpan diam-diam. Itu memang perilaku jalur pending sesuai spec (dikonsumsi nanti di Detail). Tidak ada perubahan lain.

- [ ] **Step 5: Tulis test repository**

Create `app/src/test/java/com/secondbrain/app/data/repository/NoteRepositoryGroupTest.kt`:

```kotlin
package com.secondbrain.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.secondbrain.app.data.database.AppDatabase
import com.secondbrain.app.data.model.Metadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteRepositoryGroupTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: NoteRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = NoteRepository(db.noteDao(), db.reminderDao(), db.groupDao())
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun resolve_pakaiUlangGrupExisting_caseInsensitiveDanTrim() = runBlocking {
        val a = repo.resolveOrCreateGroup("Renovasi Rumah")!!
        val b = repo.resolveOrCreateGroup("  renovasi rumah ")!!
        assertEquals(a.id, b.id)
        assertEquals(1, repo.activeGroups().first().size)
    }

    @Test
    fun resolve_namaKosong_null() = runBlocking {
        assertNull(repo.resolveOrCreateGroup("   "))
    }

    @Test
    fun resolve_menghidupkanGrupTerarsip() = runBlocking {
        val g = repo.resolveOrCreateGroup("Lama")!!
        repo.setGroupArchived(g.id, true)
        val revived = repo.resolveOrCreateGroup("lama")!!
        assertEquals(g.id, revived.id)
        assertFalse(revived.isArchived)
    }

    @Test
    fun saveDenganGroupNames_menulisKeanggotaan() = runBlocking {
        val id = repo.save(rawText = "catatan", metadata = Metadata(title = "t"),
            groupNames = listOf("Proyek A", "proyek a", "Proyek B"))
        val groups = repo.groupsOfNote(id).first().map { it.name }
        assertEquals(listOf("Proyek A", "Proyek B"), groups.sorted())
    }

    @Test
    fun renameGroup_tolakBentrok() = runBlocking {
        val a = repo.resolveOrCreateGroup("Satu")!!
        repo.resolveOrCreateGroup("Dua")!!
        assertFalse(repo.renameGroup(a.id, "dua"))
        assertTrue(repo.renameGroup(a.id, "Tiga"))
        assertEquals("Tiga", repo.getGroup(a.id)!!.name)
    }

    @Test
    fun consumeSuggestion_terima() = runBlocking {
        val id = repo.save(rawText = "c",
            metadata = Metadata(title = "t", suggestedGroups = listOf("Saran A", "Saran B")))
        repo.consumeGroupSuggestion(id, "saran a", accept = true)

        assertEquals(listOf("Saran A"), repo.groupsOfNote(id).first().map { it.name })
        val meta = repo.metadataFrom(repo.getById(id)!!)!!
        assertEquals(listOf("Saran B"), meta.suggestedGroups)
    }

    @Test
    fun consumeSuggestion_tolak_hanyaMenghapusSaran() = runBlocking {
        val id = repo.save(rawText = "c",
            metadata = Metadata(title = "t", suggestedGroups = listOf("Saran A")))
        repo.consumeGroupSuggestion(id, "Saran A", accept = false)

        assertTrue(repo.groupsOfNote(id).first().isEmpty())
        assertNull(repo.metadataFrom(repo.getById(id)!!)!!.suggestedGroups)
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/secondbrain/app app/src/test
git commit -m "feat: repository grup — resolve nama case-insensitive, keanggotaan, konsumsi saran + tests"
```

---

### Task 5: Preview — chip grup + state pilihan di InputViewModel

**Files:**
- Create: `app/src/main/java/com/secondbrain/app/ui/components/GroupChips.kt`
- Modify: `app/src/main/java/com/secondbrain/app/viewmodel/InputViewModel.kt`
- Modify: `app/src/main/java/com/secondbrain/app/ui/screens/PreviewScreen.kt`

**Interfaces:**
- Consumes: `repo.activeGroups()`, `repo.activeGroupNames()`, `repo.save(groupNames=...)` (Task 4); `Metadata.suggestedGroups` (Task 3).
- Produces (dipakai Task 6):
  - `@Composable fun GroupChip(label: String, selected: Boolean, isDark: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null)`
  - `@Composable fun GroupPickerSection(selectedNames: List<String>, suggestions: List<String>, existingNames: List<String>, onToggle: (String) -> Unit)`
  - `@Composable fun GroupPickerDialog(existingNames: List<String>, alreadySelected: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit)`
  - `InputViewModel.selectedGroups: StateFlow<List<String>>`, `toggleGroup(name)`, `activeGroups: Flow<List<GroupEntity>>`

- [ ] **Step 1: Komponen chip bersama**

Create `app/src/main/java/com/secondbrain/app/ui/components/GroupChips.kt`:

```kotlin
package com.secondbrain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.theme.*

/** Chip satu grup. onRemove != null → ikon ✕ kecil di kanan (lepas dari grup). */
@Composable
fun GroupChip(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val bg = when {
        selected && isDark -> Lavender600.copy(alpha = 0.3f)
        selected           -> Lavender100
        isDark             -> GlassDark
        else               -> GlassLight
    }
    val border = when {
        selected && isDark -> Lavender400.copy(alpha = 0.5f)
        selected           -> Lavender400
        isDark             -> GlassBorderDark
        else               -> GlassBorderLight
    }
    val fg = when {
        selected && isDark -> Lavender200
        selected           -> Lavender600
        isDark             -> Lavender400
        else               -> Gray600
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.Close, "Lepas grup",
                tint = fg,
                modifier = Modifier.size(14.dp).clickable(onClick = onRemove)
            )
        }
    }
}

/**
 * Baris chip untuk MEMILIH grup catatan (dipakai Preview).
 * Chip = saran AI + pilihan manual; saran yang belum ada di DB berlabel "(baru)".
 * Chip "+ Grup" membuka picker (pilih existing / buat baru).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupPickerSection(
    selectedNames: List<String>,
    suggestions: List<String>,
    existingNames: List<String>,
    onToggle: (String) -> Unit
) {
    val isDark = isSystemDark()
    var showPicker by remember { mutableStateOf(false) }

    fun selected(name: String) = selectedNames.any { it.trim().equals(name.trim(), ignoreCase = true) }
    fun existing(name: String) = existingNames.any { it.trim().equals(name.trim(), ignoreCase = true) }

    val shown = (suggestions + selectedNames).distinctBy { it.trim().lowercase() }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        shown.forEach { name ->
            val label = if (existing(name)) name.trim() else "${name.trim()} (baru)"
            GroupChip(label, selected(name), isDark, onClick = { onToggle(name.trim()) })
        }
        GroupChip("+ Grup", selected = false, isDark = isDark, onClick = { showPicker = true })
    }
    if (shown.isEmpty()) {
        Text(
            "Tidak ada saran grup. Ketuk + Grup untuk memilih/membuat.",
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender400 else Gray400
        )
    }

    if (showPicker) {
        GroupPickerDialog(
            existingNames = existingNames,
            alreadySelected = selectedNames,
            onPick = { onToggle(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/** Dialog pilih grup existing atau buat grup baru (nama diketik). */
@Composable
fun GroupPickerDialog(
    existingNames: List<String>,
    alreadySelected: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isSystemDark()
    var newName by remember { mutableStateOf("") }
    val candidates = existingNames.filterNot { e ->
        alreadySelected.any { it.trim().equals(e.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih grup") },
        text = {
            Column {
                if (candidates.isEmpty()) {
                    Text("Belum ada grup lain.", style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray600)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(candidates) { name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(name) }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Grup baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newName.isNotBlank()) onPick(newName.trim()) },
                enabled = newName.isNotBlank()
            ) { Text("Buat & pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
```

- [ ] **Step 2: State pilihan grup di `InputViewModel`**

(a) Tambah setelah blok `_attachments`/`clearAttachments`:

```kotlin
    // ---- Grup catatan (dipilih di Preview; saran AI pra-centang bila cocok grup existing) ----
    private val _selectedGroups = MutableStateFlow<List<String>>(emptyList())
    val selectedGroups: StateFlow<List<String>> = _selectedGroups.asStateFlow()
    /** Daftar grup aktif untuk picker di Preview. */
    val activeGroups = repo.activeGroups()

    fun toggleGroup(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        _selectedGroups.update { cur ->
            if (cur.any { it.equals(clean, ignoreCase = true) })
                cur.filterNot { it.equals(clean, ignoreCase = true) }
            else cur + clean
        }
    }
```

(b) Di `processWithAI`, dalam `.onSuccess { meta ->` setelah blok `_useAlarm.value = ...` dan sebelum `_uiState.value = InputUiState.Preview(meta)`, tambah:

```kotlin
                    // Saran yang cocok grup EXISTING → pra-centang; usulan baru TIDAK dicentang.
                    val existing = repo.activeGroupNames()
                    _selectedGroups.value = meta.suggestedGroups.orEmpty().filter { s ->
                        existing.any { it.trim().equals(s.trim(), ignoreCase = true) }
                    }
```

(c) Di `saveNote`, ubah pemanggilan `repo.save(...)`:

```kotlin
                repo.save(
                    rawText = _rawText.value.trim(),
                    metadata = metadata.copy(suggestedGroups = null),   // saran sudah dikonsumsi
                    prioritas = _selectedPrioritas.value,
                    status = _selectedStatus.value,
                    alarmOffsetMinutes = prefs.getAlarmOffsetMinutes(),
                    useAlarm = _useAlarm.value,
                    attachments = _attachments.value,
                    groupNames = _selectedGroups.value
                )
```

(d) Di `reset()`, tambah setelah `_attachments.value = emptyList()`:

```kotlin
        _selectedGroups.value = emptyList()
```

- [ ] **Step 3: Kartu grup di `PreviewScreen`**

Di `PreviewScreen.kt`, setelah blok warning `WarningBox("Tanggal tidak terdeteksi...")` yang ditutup `}` dan baris `Spacer(Modifier.height(12.dp))` (persis sebelum komentar `// Manual fields`), sisipkan:

```kotlin
            // Grup catatan: saran AI (cocok existing = tercentang; baru = tidak) + pilihan manual
            val activeGroups by vm.activeGroups.collectAsState(initial = emptyList())
            val selectedGroups by vm.selectedGroups.collectAsState()
            GlassCard {
                SectionLabel("grup", modifier = Modifier.padding(bottom = 8.dp))
                GroupPickerSection(
                    selectedNames = selectedGroups,
                    suggestions = metadata.suggestedGroups.orEmpty(),
                    existingNames = activeGroups.map { it.name },
                    onToggle = vm::toggleGroup
                )
            }

            Spacer(Modifier.height(12.dp))
```

(Import `com.secondbrain.app.ui.components.*` sudah ada di file ini — tidak perlu import baru.)

**Deviasi kecil yang disengaja dari spec §5:** saran AI yang menunjuk grup TERARSIP tampil sebagai chip "(baru)" tidak tercentang (bukan disembunyikan), karena `existingNames` hanya berisi grup aktif; bila user mencentangnya, `resolveOrCreateGroup` menghidupkan kembali grup arsip tersebut. Perilaku ini tetap memenuhi prinsip inti spec (tidak ada yang otomatis, tidak ada grup kembar) dan lebih jujur ke user daripada menyembunyikan saran.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/secondbrain/app/ui/components/GroupChips.kt app/src/main/java/com/secondbrain/app/viewmodel/InputViewModel.kt app/src/main/java/com/secondbrain/app/ui/screens/PreviewScreen.kt
git commit -m "feat: chip grup di Preview — saran AI pra-centang, usulan baru opsional, picker manual"
```

---

### Task 6: Detail — kelola grup catatan + terima/tolak saran (jalur pending)

**Files:**
- Modify: `app/src/main/java/com/secondbrain/app/viewmodel/NoteDetailViewModel.kt`
- Modify: `app/src/main/java/com/secondbrain/app/ui/screens/NoteDetailScreen.kt`
- Modify: `app/src/main/java/com/secondbrain/app/navigation/NavGraph.kt` (param `onOpenGroup` — rute tujuan dibuat Task 7; sementara arahkan ke no-op agar kompilasi, lalu Task 7 menggantinya)

**Interfaces:**
- Consumes: `GroupChip`, `GroupPickerDialog` (Task 5); repo grup (Task 4).
- Produces:
  - `NoteDetailViewModel.groupsOf(noteId): Flow<List<GroupEntity>>`, `activeGroups(): Flow<List<GroupEntity>>`, `addToGroup(name)`, `removeFromGroup(groupId)`, `acceptGroupSuggestion(name)`, `rejectGroupSuggestion(name)`
  - `NoteDetailScreen(vm, noteId, onBack, onDeleted, onOpenGroup: (Long) -> Unit)` — parameter baru.

- [ ] **Step 1: Fungsi grup di `NoteDetailViewModel`**

Tambah di akhir class (sebelum `clearMessage`). Import yang dibutuhkan sudah tercakup `com.secondbrain.app.data.model.*`:

```kotlin
    // ---- Grup ----

    fun groupsOf(noteId: Long) = repo.groupsOfNote(noteId)
    fun activeGroups() = repo.activeGroups()

    fun addToGroup(name: String) {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            repo.assignGroups(note.id, listOf(name))
        }
    }

    fun removeFromGroup(groupId: Long) {
        val note = _state.value.note ?: return
        viewModelScope.launch { repo.removeNoteFromGroup(note.id, groupId) }
    }

    fun acceptGroupSuggestion(name: String) = consumeSuggestion(name, accept = true)
    fun rejectGroupSuggestion(name: String) = consumeSuggestion(name, accept = false)

    private fun consumeSuggestion(name: String, accept: Boolean) {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            repo.consumeGroupSuggestion(note.id, name, accept)
            val refreshed = repo.getById(note.id)
            _state.value = _state.value.copy(
                note = refreshed,
                metadata = refreshed?.let { repo.metadataFrom(it) },
                message = if (accept) "Ditambahkan ke grup \"${name.trim()}\"" else null
            )
        }
    }
```

- [ ] **Step 2: Seksi grup di `NoteDetailScreen`**

(a) Ubah tanda tangan composable:

```kotlin
fun NoteDetailScreen(
    vm: NoteDetailViewModel,
    noteId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenGroup: (Long) -> Unit
) {
```

(b) Sisipkan kartu grup SETELAH blok metadata (`if (meta != null) { GlassCard { ... } Spacer(Modifier.height(10.dp)) }`) dan SEBELUM komentar `// Management: priority & status`:

```kotlin
            // Grup catatan: keanggotaan + saran AI yang belum dikonsumsi (jalur pending)
            val noteGroups by vm.groupsOf(note.id).collectAsState(initial = emptyList())
            val allGroups by vm.activeGroups().collectAsState(initial = emptyList())
            var showGroupPicker by remember { mutableStateOf(false) }
            GlassCard {
                SectionLabel("grup", modifier = Modifier.padding(bottom = 6.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    noteGroups.forEach { g ->
                        GroupChip(
                            label = g.name,
                            selected = true,
                            isDark = isDark,
                            onClick = { onOpenGroup(g.id) },
                            onRemove = { vm.removeFromGroup(g.id) }
                        )
                    }
                    GroupChip("+ Grup", selected = false, isDark = isDark,
                        onClick = { showGroupPicker = true })
                }

                // Saran AI (tap = terima, ✕ = tolak) — hanya yang belum jadi anggota
                val pendingSuggestions = meta?.suggestedGroups.orEmpty().filterNot { s ->
                    noteGroups.any { it.name.trim().equals(s.trim(), ignoreCase = true) }
                }
                if (pendingSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Saran AI — ketuk untuk menerima:",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Lavender400 else Gray600)
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pendingSuggestions.forEach { s ->
                            GroupChip(
                                label = s.trim(),
                                selected = false,
                                isDark = isDark,
                                onClick = { vm.acceptGroupSuggestion(s) },
                                onRemove = { vm.rejectGroupSuggestion(s) }
                            )
                        }
                    }
                }
            }
            if (showGroupPicker) {
                GroupPickerDialog(
                    existingNames = allGroups.map { it.name },
                    alreadySelected = noteGroups.map { it.name },
                    onPick = { vm.addToGroup(it); showGroupPicker = false },
                    onDismiss = { showGroupPicker = false }
                )
            }
            Spacer(Modifier.height(10.dp))
```

(Import `com.secondbrain.app.ui.components.*` sudah ada. `FlowRow` berasal dari `androidx.compose.foundation.layout` yang sudah di-import via wildcard.)

(c) Di `NavGraph.kt`, pada composable `Screen.Detail.route`, tambah argumen sementara (Task 7 mengganti dengan navigasi sungguhan):

```kotlin
            NoteDetailScreen(
                vm = detailVm,
                noteId = noteId,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack(Screen.Home.route, false) },
                onOpenGroup = { }   // diganti Task 7: navigasi ke isi grup
            )
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/secondbrain/app/viewmodel/NoteDetailViewModel.kt app/src/main/java/com/secondbrain/app/ui/screens/NoteDetailScreen.kt app/src/main/java/com/secondbrain/app/navigation/NavGraph.kt
git commit -m "feat: kelola grup di Detail + terima/tolak saran AI jalur pending"
```

---

### Task 7: Layar Grup (daftar + isi) + navigasi + pintu masuk

**Files:**
- Create: `app/src/main/java/com/secondbrain/app/ui/screens/GroupsScreen.kt`
- Create: `app/src/main/java/com/secondbrain/app/ui/screens/GroupNotesScreen.kt`
- Modify: `app/src/main/java/com/secondbrain/app/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/secondbrain/app/ui/screens/HomePagerScreen.kt`
- Modify: `app/src/main/java/com/secondbrain/app/ui/screens/NotesHomePage.kt`

**Interfaces:**
- Consumes: repo grup (Task 4), `GroupChip` tidak dipakai di sini; `NoteCard` existing (`NoteCard(title, type, timeRange, prioritas, status, onClick, modifier)`).
- Produces: rute `Screen.Groups ("groups")` dan `Screen.GroupNotes ("group/{groupId}")`; `HomePagerScreen`/`NotesHomePage` param baru `onOpenGroups: () -> Unit`.

- [ ] **Step 1: Layar daftar grup**

Create `app/src/main/java/com/secondbrain/app/ui/screens/GroupsScreen.kt`:

```kotlin
package com.secondbrain.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.database.GroupWithCount
import com.secondbrain.app.data.model.GroupEntity
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import kotlinx.coroutines.launch

/** Daftar grup aktif + jumlah catatan; buat/rename/arsip/hapus grup. */
@Composable
fun GroupsScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onGroupClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val groups by repo.activeGroupsWithCount().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<GroupEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<GroupWithCount?>(null) }

    Scaffold(
        containerColor = if (isDark) Lavender900 else Gray50,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali",
                        tint = if (isDark) Lavender200 else Lavender600)
                }
                Text("Grup", style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800)
                Spacer(Modifier.weight(1f))
                GlassButton("Grup baru", onClick = { showCreate = true },
                    icon = Icons.Outlined.Add, accent = true)
            }
            Spacer(Modifier.height(12.dp))

            if (groups.isEmpty()) {
                Text(
                    "Belum ada grup. Buat lewat tombol di atas, atau centang saran AI saat menyimpan catatan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it.group.id }) { g ->
                    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onGroupClick(g.group.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(g.group.name, style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) Lavender50 else Lavender800)
                                Text("${g.noteCount} catatan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Lavender400 else Gray600)
                            }
                            IconButton(onClick = { renameTarget = g.group }) {
                                Icon(Icons.Outlined.Edit, "Ganti nama",
                                    tint = if (isDark) Lavender400 else Lavender600,
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    repo.setGroupArchived(g.group.id, true)
                                    snackbar.showSnackbar("Grup \"${g.group.name}\" diarsipkan")
                                }
                            }) {
                                Icon(Icons.Outlined.Archive, "Arsipkan",
                                    tint = if (isDark) Lavender400 else Lavender600,
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { deleteTarget = g }) {
                                Icon(Icons.Outlined.DeleteOutline, "Hapus", tint = Rose600,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        GroupNameDialog(
            title = "Grup baru",
            initial = "",
            onConfirm = { name ->
                scope.launch {
                    repo.resolveOrCreateGroup(name)
                    showCreate = false
                }
            },
            onDismiss = { showCreate = false }
        )
    }

    renameTarget?.let { target ->
        GroupNameDialog(
            title = "Ganti nama grup",
            initial = target.name,
            onConfirm = { name ->
                scope.launch {
                    val ok = repo.renameGroup(target.id, name)
                    if (!ok) snackbar.showSnackbar("Nama \"$name\" sudah dipakai grup lain")
                    renameTarget = null
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus grup \"${target.group.name}\"?") },
            text = { Text("${target.noteCount} catatan akan dikeluarkan dari grup ini. Catatannya sendiri TIDAK dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteGroup(target.group.id)
                        deleteTarget = null
                    }
                }) { Text("Hapus", color = Rose600) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } }
        )
    }
}

/** Dialog input nama grup (buat baru / ganti nama). */
@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama grup") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
```

- [ ] **Step 2: Layar isi grup**

Create `app/src/main/java/com/secondbrain/app/ui/screens/GroupNotesScreen.kt`:

```kotlin
package com.secondbrain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.NoteCard
import com.secondbrain.app.ui.components.SectionLabel
import com.secondbrain.app.ui.components.isSystemDark
import com.secondbrain.app.ui.theme.*

/** Daftar catatan di dalam satu grup. */
@Composable
fun GroupNotesScreen(
    repo: NoteRepository,
    groupId: Long,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    var group by remember { mutableStateOf<GroupEntity?>(null) }
    LaunchedEffect(groupId) { group = repo.getGroup(groupId) }
    val notes by repo.notesInGroup(groupId).collectAsState(initial = emptyList())

    Scaffold(containerColor = if (isDark) Lavender900 else Gray50) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali",
                        tint = if (isDark) Lavender200 else Lavender600)
                }
                Text(group?.name ?: "Grup", style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800)
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("${notes.size} catatan")
            Spacer(Modifier.height(4.dp))

            if (notes.isEmpty()) {
                Text("Belum ada catatan di grup ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes, key = { it.id }) { note ->
                    val meta = repo.metadataFrom(note)
                    NoteCard(
                        title = meta?.title?.ifBlank { note.rawText.take(60) } ?: note.rawText.take(60),
                        type = meta?.type ?: NoteType.NOTE,
                        timeRange = buildString {
                            meta?.startTime?.let { append(it) }
                            meta?.endTime?.let { append(" – $it") }
                        }.ifBlank { null },
                        prioritas = note.prioritas?.let { runCatching { Priority.valueOf(it) }.getOrNull() },
                        status = note.status?.let { runCatching { NoteStatus.valueOf(it) }.getOrNull() },
                        onClick = { onNoteClick(note.id) }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Rute navigasi**

Di `NavGraph.kt`:

(a) Tambah di sealed class `Screen` (setelah `object Finance`):

```kotlin
    object Groups     : Screen("groups")
    object GroupNotes : Screen("group/{groupId}") {
        fun go(groupId: Long) = "group/$groupId"
    }
```

(b) Tambah dua composable (setelah blok `composable(Screen.Finance.route) { ... }`):

```kotlin
        composable(Screen.Groups.route) {
            GroupsScreen(
                repo = repo,
                onBack = { navController.popBackStack() },
                onGroupClick = { id -> navController.navigate(Screen.GroupNotes.go(id)) }
            )
        }

        composable(
            route = Screen.GroupNotes.route,
            arguments = listOf(navArgument("groupId") { type = NavType.LongType })
        ) { back ->
            val groupId = back.arguments?.getLong("groupId") ?: -1L
            GroupNotesScreen(
                repo = repo,
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onNoteClick = { id -> navController.navigate(Screen.Detail.go(id)) }
            )
        }
```

(c) Di composable `Screen.Home.route`, tambah argumen ke `HomePagerScreen` (setelah `onOpenFinance = ...`):

```kotlin
                onOpenGroups = { navController.navigate(Screen.Groups.route) },
```

(d) Ganti no-op Task 6 di composable `Screen.Detail.route`:

```kotlin
                onOpenGroup = { id -> navController.navigate(Screen.GroupNotes.go(id)) }
```

- [ ] **Step 4: Pintu masuk di halaman daftar catatan**

(a) `HomePagerScreen.kt` — tambah parameter setelah `onOpenFinance: () -> Unit,`:

```kotlin
    onOpenGroups: () -> Unit,
```

dan teruskan ke `NotesHomePage` (setelah `onOpenFinance = onOpenFinance,`):

```kotlin
                onOpenGroups = onOpenGroups,
```

(b) `NotesHomePage.kt` — tambah parameter setelah `onOpenFinance: () -> Unit,`:

```kotlin
    onOpenGroups: () -> Unit,
```

dan tambah ikon di baris pintasan atas, SEBELUM `Icon(Icons.Outlined.Timeline, ...)`:

```kotlin
                    Icon(Icons.Outlined.Folder, "Halaman grup",
                        tint = if (isDark) Lavender400 else Lavender600,
                        modifier = Modifier.size(22.dp).clickable(onClick = onOpenGroups))
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/secondbrain/app/ui/screens app/src/main/java/com/secondbrain/app/navigation/NavGraph.kt
git commit -m "feat: layar Grup (daftar + isi grup) + navigasi + pintasan di halaman catatan"
```

---

### Task 8: Panduan testing device + checkpoint CI

**Files:**
- Modify: `TESTING.md` (file untracked di root repo — tambah seksi baru, JANGAN ubah isi lain)

**Interfaces:**
- Consumes: seluruh fitur Task 2–7.
- Produces: checklist verifikasi device; sinyal selesai untuk push user.

- [ ] **Step 1: Tambah skenario tes grup di `TESTING.md`**

Tambahkan di akhir seksi `B. Skenario Testing` (sebelum `## Catatan Lingkungan`):

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add TESTING.md
git commit -m "docs: skenario tes device untuk fitur grup catatan"
```

- [ ] **Step 3: Checkpoint CI (minta user push)**

Sampaikan ke user: semua task selesai & ter-commit lokal. Minta user `git push origin main`, lalu pantau GitHub Actions:

- Expected: step **Run unit tests** hijau (SanityTest, GroupDaoTest, SuggestedGroupsParsingTest, NoteRepositoryGroupTest lulus) dan **Build debug APK** hijau.
- Jika merah: baca log CI, perbaiki, commit, minta push ulang. JANGAN klaim selesai sebelum CI hijau.
- Setelah CI hijau: user install APK di device dan menjalankan TES 29–35.
