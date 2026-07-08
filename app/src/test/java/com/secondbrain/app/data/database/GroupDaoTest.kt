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
