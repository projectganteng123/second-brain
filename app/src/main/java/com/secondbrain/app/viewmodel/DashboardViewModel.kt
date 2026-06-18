package com.secondbrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondbrain.app.data.model.NoteEntity
import com.secondbrain.app.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class DashboardTab { HARI_INI, MINGGU_INI, AKAN_DATANG }

class DashboardViewModel(private val repo: NoteRepository) : ViewModel() {

    val activeNotes: StateFlow<List<NoteEntity>> = repo.getAllActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedTab = MutableStateFlow(DashboardTab.HARI_INI)
    val selectedTab: StateFlow<DashboardTab> = _selectedTab.asStateFlow()

    val filteredNotes: StateFlow<List<NoteEntity>> = combine(activeNotes, _selectedTab) { notes, tab ->
        val today = LocalDate.now()
        when (tab) {
            DashboardTab.HARI_INI     -> repo.getNotesForDate(today, notes)
            DashboardTab.MINGGU_INI   -> repo.getNotesForRange(today, today.plusDays(6), notes)
            DashboardTab.AKAN_DATANG  -> repo.getNotesForRange(today, today.plusDays(89), notes)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayCount: StateFlow<Int> = activeNotes.map { notes ->
        repo.getNotesForDate(LocalDate.now(), notes).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val weekCount: StateFlow<Int> = activeNotes.map { notes ->
        val today = LocalDate.now()
        repo.getNotesForRange(today, today.plusDays(6), notes).size
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingCount: StateFlow<Int> = activeNotes.map { notes ->
        notes.count { it.status == null || it.status == "BELUM_MULAI" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun selectTab(tab: DashboardTab) { _selectedTab.value = tab }

    fun archive(note: NoteEntity) = viewModelScope.launch { repo.setArchived(note.id, true) }
    fun delete(note: NoteEntity) = viewModelScope.launch { repo.delete(note) }
}
