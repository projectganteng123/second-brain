package com.secondbrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondbrain.app.ai.AIService
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.util.DebugLog
import com.secondbrain.app.util.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class NoteDetailState(
    val note: NoteEntity? = null,
    val metadata: Metadata? = null,
    val loading: Boolean = true,
    val reExtracting: Boolean = false,
    val deleted: Boolean = false,
    val message: String? = null
)

class NoteDetailViewModel(
    private val repo: NoteRepository,
    private val prefs: PrefsManager
) : ViewModel() {

    private val _state = MutableStateFlow(NoteDetailState())
    val state: StateFlow<NoteDetailState> = _state.asStateFlow()

    fun load(noteId: Long) {
        viewModelScope.launch {
            val note = repo.getById(noteId)
            _state.value = NoteDetailState(
                note = note,
                metadata = note?.let { repo.metadataFrom(it) },
                loading = false
            )
        }
    }

    fun setPrioritas(p: Priority?) {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            repo.setPrioritas(note.id, p)
            _state.value = _state.value.copy(note = note.copy(prioritas = p?.name))
        }
    }

    fun setStatus(s: NoteStatus?) {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            repo.setStatus(note.id, s)
            _state.value = _state.value.copy(note = note.copy(status = s?.name))
        }
    }

    fun setUseAlarm(v: Boolean) {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            repo.setUseAlarm(note.id, v, prefs.getAlarmOffsetMinutes())
            _state.value = _state.value.copy(
                note = note.copy(useAlarm = v),
                message = if (v) "Pengingat dijadikan alarm" else "Alarm dimatikan"
            )
        }
    }

    fun toggleArchive() {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            val newVal = !note.isArchived
            repo.setArchived(note.id, newVal)
            _state.value = _state.value.copy(
                note = note.copy(isArchived = newVal),
                message = if (newVal) "Catatan diarsipkan" else "Catatan dikembalikan"
            )
        }
    }

    fun delete() {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            repo.delete(note)
            _state.value = _state.value.copy(deleted = true)
        }
    }

    fun reExtract(newRawText: String) {
        val note = _state.value.note ?: return
        if (!prefs.hasAnyActiveApiKey()) {
            _state.value = _state.value.copy(message = "API key belum diatur atau tidak ada provider yang dicentang.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(reExtracting = true, message = null)
            // Seluruh blok dibungkus agar error apa pun tidak menutup aplikasi
            runCatching {
                val now = com.secondbrain.app.ai.PromptTemplates.nowString()
                val service = AIService.forExtraction(prefs)
                val result = service.extractMetadata(newRawText, now, repo.activeGroupNames())
                result.onSuccess { meta ->
                    repo.update(note.copy(rawText = newRawText, updatedAt = System.currentTimeMillis()))
                    repo.updateMetadata(note.id, meta, prefs.getAlarmOffsetMinutes())
                    val refreshed = repo.getById(note.id)
                    _state.value = _state.value.copy(
                        note = refreshed,
                        metadata = meta,
                        reExtracting = false,
                        message = "Metadata diperbarui"
                    )
                }.onFailure { e ->
                    _state.value = _state.value.copy(
                        reExtracting = false,
                        message = "Gagal memproses: ${e.message}"
                    )
                }
            }.onFailure { e ->
                DebugLog.log("Detail ✕ reExtract", e.stackTraceToString().take(800))
                _state.value = _state.value.copy(
                    reExtracting = false,
                    message = "Terjadi kesalahan: ${e.message}"
                )
            }
        }
    }

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

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
