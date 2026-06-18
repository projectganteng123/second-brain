package com.secondbrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondbrain.app.ai.AIConfig
import com.secondbrain.app.ai.GeminiProvider
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
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
    private val apiKey: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<InputUiState>(InputUiState.Idle)
    val uiState: StateFlow<InputUiState> = _uiState.asStateFlow()

    private val _rawText = MutableStateFlow("")
    val rawText: StateFlow<String> = _rawText.asStateFlow()

    private val _selectedPrioritas = MutableStateFlow<Priority?>(null)
    val selectedPrioritas: StateFlow<Priority?> = _selectedPrioritas.asStateFlow()

    private val _selectedStatus = MutableStateFlow<NoteStatus?>(NoteStatus.BELUM_MULAI)
    val selectedStatus: StateFlow<NoteStatus?> = _selectedStatus.asStateFlow()

    fun updateText(text: String) { _rawText.value = text }
    fun setPrioritas(p: Priority?) { _selectedPrioritas.value = p }
    fun setStatus(s: NoteStatus?) { _selectedStatus.value = s }

    fun processWithAI() {
        val text = _rawText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.value = InputUiState.Extracting
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            val provider = GeminiProvider(AIConfig(apiKey))
            provider.extractMetadata(text, now)
                .onSuccess { _uiState.value = InputUiState.Preview(it) }
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
                    status = _selectedStatus.value
                )
            }.onSuccess { _uiState.value = InputUiState.Saved }
             .onFailure { _uiState.value = InputUiState.Error(it.message ?: "Gagal menyimpan") }
        }
    }

    fun savePendingOffline() {
        viewModelScope.launch {
            _uiState.value = InputUiState.Saving
            runCatching {
                repo.savePending(_rawText.value.trim(), InputSource.TEXT)
            }.onSuccess { _uiState.value = InputUiState.Saved }
             .onFailure { _uiState.value = InputUiState.Error(it.message ?: "Gagal menyimpan") }
        }
    }

    fun reset() {
        _rawText.value = ""
        _selectedPrioritas.value = null
        _selectedStatus.value = NoteStatus.BELUM_MULAI
        _uiState.value = InputUiState.Idle
    }
}
