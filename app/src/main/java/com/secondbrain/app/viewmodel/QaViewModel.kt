package com.secondbrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondbrain.app.ai.AIService
import com.secondbrain.app.data.model.NoteEntity
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.util.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class QaSource(val id: Long, val title: String)

sealed class QaState {
    object Idle : QaState()
    object Thinking : QaState()
    data class Answer(val text: String, val sources: List<QaSource>) : QaState()
    object NoResults : QaState()
    data class Error(val message: String) : QaState()
}

class QaViewModel(
    private val repo: NoteRepository,
    private val prefs: PrefsManager
) : ViewModel() {

    private val _state = MutableStateFlow<QaState>(QaState.Idle)
    val state: StateFlow<QaState> = _state.asStateFlow()

    private val _question = MutableStateFlow("")
    val question: StateFlow<String> = _question.asStateFlow()

    fun updateQuestion(q: String) { _question.value = q }

    fun ask() {
        val q = _question.value.trim()
        if (q.isBlank()) return
        val keys = prefs.getApiKeys()
        if (keys.isEmpty()) {
            _state.value = QaState.Error("API key Gemini belum diatur. Buka Pengaturan terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            _state.value = QaState.Thinking
            val allNotes = repo.getAllActive().first()
            val relevant = repo.retrieveRelevant(q, allNotes)

            if (relevant.isEmpty()) {
                _state.value = QaState.NoResults
                return@launch
            }

            val context = relevant.map { repo.contextStringFor(it) }
            val sources = relevant.map { note ->
                val title = repo.metadataFrom(note)?.title?.ifBlank { note.rawText.take(40) }
                    ?: note.rawText.take(40)
                QaSource(note.id, title)
            }

            val service = AIService(keys, prefs.getModel(), PrefsManager.MODEL_OPTIONS, null)
            service.answerQuestion(q, context)
                .onSuccess { _state.value = QaState.Answer(it, sources) }
                .onFailure { _state.value = QaState.Error(it.message ?: "Gagal menjawab") }
        }
    }

    fun reset() {
        _question.value = ""
        _state.value = QaState.Idle
    }
}
