package com.secondbrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondbrain.app.ai.AIService
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.util.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

sealed class InputUiState {
    object Idle : InputUiState()
    object Extracting : InputUiState()
    data class Preview(val metadata: Metadata) : InputUiState()
    object Saving : InputUiState()
    object Saved : InputUiState()
    data class Error(val message: String) : InputUiState()
}

class InputViewModel(
    private val repo: NoteRepository,
    private val prefs: PrefsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<InputUiState>(InputUiState.Idle)
    val uiState: StateFlow<InputUiState> = _uiState.asStateFlow()

    private val _rawText = MutableStateFlow("")
    val rawText: StateFlow<String> = _rawText.asStateFlow()

    private val _selectedPrioritas = MutableStateFlow<Priority?>(null)
    val selectedPrioritas: StateFlow<Priority?> = _selectedPrioritas.asStateFlow()

    private val _selectedStatus = MutableStateFlow<NoteStatus?>(NoteStatus.BELUM_MULAI)
    val selectedStatus: StateFlow<NoteStatus?> = _selectedStatus.asStateFlow()

    private val _useAlarm = MutableStateFlow(false)
    val useAlarm: StateFlow<Boolean> = _useAlarm.asStateFlow()

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()

    fun addAttachment(a: Attachment) { _attachments.value = _attachments.value + a }
    fun removeAttachment(a: Attachment) { _attachments.value = _attachments.value - a }
    fun clearAttachments() { _attachments.value = emptyList() }

    // Apakah user sudah mengubah prioritas/status manual (agar rekomendasi AI tidak menimpa pilihan user)
    private var userTouchedPrioritas = false
    private var userTouchedStatus = false

    fun updateText(text: String) { _rawText.value = text }
    fun setPrioritas(p: Priority?) { _selectedPrioritas.value = p; userTouchedPrioritas = true }
    fun setStatus(s: NoteStatus?) { _selectedStatus.value = s; userTouchedStatus = true }
    fun setUseAlarm(v: Boolean) { _useAlarm.value = v }
    fun appendText(extra: String) {
        if (extra.isBlank()) return
        val cur = _rawText.value
        _rawText.value = if (cur.isBlank()) extra else "$cur $extra"
    }

    fun processWithAI() {
        val text = _rawText.value.trim()
        if (text.isBlank()) return
        if (!prefs.hasAnyActiveApiKey()) {
            _uiState.value = InputUiState.Error("API key belum diatur atau tidak ada provider yang dicentang. Buka Pengaturan terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            _uiState.value = InputUiState.Extracting
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            val service = AIService.forExtraction(prefs)
            service.extractMetadata(text, now)
                .onSuccess { meta ->
                    // Pra-isi prioritas/status dari rekomendasi AI bila user belum memilih manual
                    if (!userTouchedPrioritas) {
                        Priority.fromString(meta.priority)?.let { _selectedPrioritas.value = it }
                    }
                    if (!userTouchedStatus) {
                        NoteStatus.fromString(meta.status)?.let { _selectedStatus.value = it }
                    }
                    _uiState.value = InputUiState.Preview(meta)
                }
                .onFailure { _uiState.value = InputUiState.Error(it.message ?: "Gagal memproses") }
        }
    }

    fun saveNote(metadata: Metadata) {
        viewModelScope.launch {
            _uiState.value = InputUiState.Saving
            runCatching {
                repo.save(
                    rawText = _rawText.value.trim(),
                    metadata = metadata,
                    prioritas = _selectedPrioritas.value,
                    status = _selectedStatus.value,
                    offsetHours = prefs.getReminderOffsetHours(),
                    useAlarm = _useAlarm.value,
                    attachments = _attachments.value
                )
            }.onSuccess { _uiState.value = InputUiState.Saved }
             .onFailure { _uiState.value = InputUiState.Error(it.message ?: "Gagal menyimpan") }
        }
    }

    fun savePendingOffline() {
        viewModelScope.launch {
            _uiState.value = InputUiState.Saving
            runCatching {
                repo.savePending(_rawText.value.trim(), InputSource.TEXT, _attachments.value)
            }.onSuccess { _uiState.value = InputUiState.Saved }
             .onFailure { _uiState.value = InputUiState.Error(it.message ?: "Gagal menyimpan") }
        }
    }

    /** Dipanggil setelah berpindah ke layar Preview, agar kembali ke Input tidak memicu
     *  navigasi ulang ke Preview (metadata sudah dibawa lewat argumen navigasi). */
    fun onPreviewNavigated() {
        if (_uiState.value is InputUiState.Preview) _uiState.value = InputUiState.Idle
    }

    fun reset() {
        _rawText.value = ""
        _selectedPrioritas.value = null
        _selectedStatus.value = NoteStatus.BELUM_MULAI
        _useAlarm.value = false
        _attachments.value = emptyList()
        userTouchedPrioritas = false
        userTouchedStatus = false
        _uiState.value = InputUiState.Idle
    }
}
