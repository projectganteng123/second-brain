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
            repo.setUseAlarm(note.id, v, prefs.getReminderOffsetHours())
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
        val keys = prefs.getApiKeys()
        if (keys.isEmpty()) {
            _state.value = _state.value.copy(message = "API key Gemini belum diatur.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(reExtracting = true, message = null)
            // Seluruh blok dibungkus agar error apa pun tidak menutup aplikasi
            runCatching {
                val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                val service = AIService(
                    keys = keys,
                    preferredModel = prefs.getModel(),
                    modelPool = PrefsManager.MODEL_OPTIONS,
                    promptTemplate = prefs.getCustomPrompt().ifBlank { null }
                )
                val result = service.extractMetadata(newRawText, now)
                result.onSuccess { meta ->
                    repo.update(note.copy(rawText = newRawText, updatedAt = System.currentTimeMillis()))
                    repo.updateMetadata(note.id, meta, prefs.getReminderOffsetHours())
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

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
