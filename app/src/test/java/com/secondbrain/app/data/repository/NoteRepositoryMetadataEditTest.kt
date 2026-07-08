package com.secondbrain.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.secondbrain.app.data.database.AppDatabase
import com.secondbrain.app.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Round-trip edit metadata manual (halaman detail): SEMUA field — termasuk
 * extraSchedules & transactions yang baru bisa diedit — harus selamat lewat
 * updateMetadata → metadataFrom, dan pengingat dibuat ulang.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NoteRepositoryMetadataEditTest {

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

    private fun futureDate(days: Long): String =
        LocalDate.now().plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE)

    @Test
    fun updateMetadata_roundTripSemuaField() = runBlocking {
        val id = repo.save(rawText = "catatan awal", metadata = Metadata(title = "Awal"))

        val edited = Metadata(
            title = "Rapat direvisi",
            type = NoteType.MEETING,
            startTime = "09:30",
            endTime = "11:00",
            locations = listOf(LocationEntry(value = "Kantor Pusat")),
            entities = Entities(people = listOf("Budi", "Sari"), organizations = listOf("PT Maju")),
            keywords = listOf("rapat", "anggaran"),
            recurrenceDates = listOf(futureDate(3)),
            actions = listOf(ActionItem(action = "Siapkan slide", owner = "Budi",
                deadline = "${futureDate(2)}T17:00")),
            summary = "Rapat anggaran kuartal",
            alarmTimes = listOf("${futureDate(3)}T08:00"),
            extraSchedules = listOf(
                ExtraSchedule(type = "task", title = "Follow-up notulen",
                    dates = listOf(futureDate(4)), startTime = "13:00", useAlarm = true)
            ),
            transactions = listOf(
                Transaction(type = "expense", item = "konsumsi rapat", category = "Makanan",
                    quantity = 2.0, unit = "box", amount = 150000.0, date = futureDate(3))
            )
        )
        repo.updateMetadata(id, edited, alarmOffsetMinutes = 15)

        val readBack = repo.metadataFrom(repo.getById(id)!!)!!
        assertEquals("Rapat direvisi", readBack.title)
        assertEquals(NoteType.MEETING, readBack.type)
        assertEquals("09:30", readBack.startTime)
        assertEquals("11:00", readBack.endTime)
        assertEquals(listOf("Kantor Pusat"), readBack.locations.map { it.value })
        assertEquals(listOf("Budi", "Sari"), readBack.entities.people)
        assertEquals(listOf("PT Maju"), readBack.entities.organizations)
        assertEquals(listOf("rapat", "anggaran"), readBack.keywords)
        assertEquals(listOf(futureDate(3)), readBack.recurrenceDates)
        assertEquals("Siapkan slide", readBack.actions.single().action)
        assertEquals("${futureDate(2)}T17:00", readBack.actions.single().deadline)
        assertEquals(listOf("${futureDate(3)}T08:00"), readBack.alarmTimes)

        val sch = readBack.extraSchedules!!.single()
        assertEquals("Follow-up notulen", sch.title)
        assertEquals(listOf(futureDate(4)), sch.dates)
        assertEquals("13:00", sch.startTime)
        assertTrue(sch.useAlarm)

        val tx = readBack.transactions!!.single()
        assertEquals("konsumsi rapat", tx.item)
        assertEquals(150000.0, tx.amount, 0.0)
        assertEquals(2.0, tx.quantity!!, 0.0)
        assertEquals(futureDate(3), tx.date)

        // Pengingat dibuat ulang untuk jadwal utama + kegiatan lain + waktu alarm
        assertTrue(db.reminderDao().getIdsByNote(id).isNotEmpty())
    }

    @Test
    fun updateMetadata_hapusJadwal_pengingatIkutBersih() = runBlocking {
        val id = repo.save(
            rawText = "meeting besok",
            metadata = Metadata(
                title = "Meeting", type = NoteType.MEETING,
                startTime = "09:00", recurrenceDates = listOf(futureDate(1))
            )
        )
        assertTrue(db.reminderDao().getIdsByNote(id).isNotEmpty())

        // User menghapus semua tanggal & waktu di editor → pengingat lama tidak boleh tersisa
        repo.updateMetadata(id, Metadata(title = "Meeting", type = NoteType.NOTE), alarmOffsetMinutes = 15)
        assertTrue(db.reminderDao().getIdsByNote(id).isEmpty())
    }

    @Test
    fun updateMetadata_catatanLamaTanpaFieldBaru_amanDiedit() = runBlocking {
        // Simulasi catatan lama: metadataJson tanpa extraSchedules/transactions/alarmTimes
        val id = repo.save(rawText = "lama", metadata = Metadata(title = "Lama"))
        val old = repo.metadataFrom(repo.getById(id)!!)!!
        assertNull(old.extraSchedules)
        assertNull(old.transactions)

        // Pola halaman detail: mulai edit dari .orEmpty(), tambah satu transaksi
        val edited = old.copy(transactions = old.transactions.orEmpty() +
            Transaction(type = "income", item = "gaji", amount = 5000000.0))
        repo.updateMetadata(id, edited, alarmOffsetMinutes = 15)

        assertEquals(5000000.0, repo.metadataFrom(repo.getById(id)!!)!!.transactions!!.single().amount, 0.0)
    }
}
