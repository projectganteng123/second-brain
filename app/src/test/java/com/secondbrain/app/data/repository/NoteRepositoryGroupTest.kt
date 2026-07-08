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
