package com.secondbrain.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondbrain.app.ai.AIService
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.util.MediaReader
import com.secondbrain.app.util.PrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

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

    private var extractJob: kotlinx.coroutines.Job? = null

    // ---- Baca gambar/file dengan AI (berjalan di latar; hasilnya menyusul ke teks) ----
    private val readJobs = java.util.Collections.synchronizedList(mutableListOf<kotlinx.coroutines.Job>())
    private val _readingCount = MutableStateFlow(0)
    val readingCount: StateFlow<Int> = _readingCount.asStateFlow()
    /** Hasil baca yang menunggu dimasukkan layar ke teks catatan. */
    private val _pendingInserts = MutableStateFlow<List<String>>(emptyList())
    val pendingInserts: StateFlow<List<String>> = _pendingInserts.asStateFlow()
    private val _readMessage = MutableStateFlow<String?>(null)
    val readMessage: StateFlow<String?> = _readMessage.asStateFlow()

    fun consumeReadMessage() { _readMessage.value = null }
    fun consumeInsert(text: String) {
        _pendingInserts.update { it - text }   // hapus kemunculan pertama
    }

    /** Mulai membaca gambar/PDF/dokumen di latar. User boleh lanjut menulis atau
     *  langsung memproses — processWithAI menunggu pembacaan selesai dulu. */
    fun readMedia(prepared: MediaReader.Prepared) {
        _readingCount.update { it + 1 }
        lateinit var job: kotlinx.coroutines.Job
        job = viewModelScope.launch {
            try {
                val result = when (prepared) {
                    is MediaReader.Prepared.Media ->
                        AIService.forVision(prefs, forPdf = prepared.mimeType == "application/pdf")
                            .readMedia(
                                prepared.mimeType, prepared.base64,
                                promptTemplate = prefs.getExtractionPrompt(com.secondbrain.app.ai.ExtractionKind.MEDIA_READ)
                                    .ifBlank { com.secondbrain.app.ai.PromptTemplates.MEDIA_READ }
                            )
                    is MediaReader.Prepared.Text ->
                        AIService.forReading(prefs).readDocument(
                            prepared.kind, prepared.text,
                            promptTemplate = prefs.getExtractionPrompt(com.secondbrain.app.ai.ExtractionKind.DOC_READ)
                                .ifBlank { com.secondbrain.app.ai.PromptTemplates.DOC_READ }
                        )
                }
                result.onSuccess { m ->
                    _pendingInserts.update { it + "Dari ${m.source}: ${m.text}" }
                }.onFailure {
                    if (it !is kotlinx.coroutines.CancellationException) {
                        com.secondbrain.app.util.DebugLog.log("Baca ✕ AI", it.message ?: it.toString())
                        _readMessage.value = it.message ?: "Gagal membaca file"
                    }
                }
            } finally {
                _readingCount.update { c -> (c - 1).coerceAtLeast(0) }
                readJobs.remove(job)
            }
        }
        readJobs += job
    }

    fun processWithAI() {
        if (!prefs.hasAnyActiveApiKey()) {
            _uiState.value = InputUiState.Error("API key belum diatur atau tidak ada provider yang dicentang. Buka Pengaturan terlebih dahulu.")
            return
        }
        if (_rawText.value.isBlank() && readJobs.isEmpty() && _pendingInserts.value.isEmpty()) return
        extractJob = viewModelScope.launch {
            _uiState.value = InputUiState.Extracting
            // Tunggu pembacaan gambar/file yang masih berjalan — hasilnya bagian dari catatan
            readJobs.toList().let { jobs -> if (jobs.isNotEmpty()) jobs.joinAll() }
            // Hasil baca yang belum sempat dimasukkan layar → gabungkan langsung ke teks
            val extra = _pendingInserts.value.filter { !_rawText.value.contains(it) }
            _pendingInserts.value = emptyList()
            if (extra.isNotEmpty()) {
                _rawText.value = (_rawText.value.trim() + " " + extra.joinToString(" ")).trim()
            }
            val text = _rawText.value.trim()
            if (text.isBlank()) {
                _uiState.value = InputUiState.Error("Tidak ada teks yang bisa diproses (pembacaan file gagal?).")
                return@launch
            }
            val now = com.secondbrain.app.ai.PromptTemplates.nowString()
            val service = AIService.forExtraction(prefs)
            service.extractMetadata(text, now, repo.activeGroupNames())
                .onSuccess { meta ->
                    // Pra-isi prioritas/status dari rekomendasi AI bila user belum memilih manual
                    if (!userTouchedPrioritas) {
                        Priority.fromString(meta.priority)?.let { _selectedPrioritas.value = it }
                    }
                    if (!userTouchedStatus) {
                        NoteStatus.fromString(meta.status)?.let { _selectedStatus.value = it }
                    }
                    // Toggle alarm di Preview: mengikuti default di Settings; rekomendasi AI
                    // hanya bisa MENYALAKAN (user tetap bisa mematikan di Preview).
                    _useAlarm.value = prefs.isEventAlarmDefaultOn() || meta.suggestAlarm
                    // Saran yang cocok grup EXISTING → pra-centang; usulan baru TIDAK dicentang.
                    val existing = repo.activeGroupNames()
                    _selectedGroups.value = meta.suggestedGroups.orEmpty().filter { s ->
                        existing.any { it.trim().equals(s.trim(), ignoreCase = true) }
                    }
                    _uiState.value = InputUiState.Preview(meta)
                }
                .onFailure {
                    if (it !is kotlinx.coroutines.CancellationException) {
                        _uiState.value = InputUiState.Error(it.message ?: "Gagal memproses")
                    }
                }
        }
    }

    /** Batalkan semua pembacaan file yang masih berjalan (hasil yang sudah masuk dipertahankan). */
    fun cancelReads() {
        synchronized(readJobs) { readJobs.forEach { it.cancel() }; readJobs.clear() }
        _readingCount.value = 0
    }

    /** Batalkan ekstraksi yang sedang berjalan (dari tombol di loading screen). */
    fun cancelExtraction() {
        extractJob?.cancel()
        extractJob = null
        if (_uiState.value is InputUiState.Extracting) _uiState.value = InputUiState.Idle
    }

    fun saveNote(metadata: Metadata) {
        viewModelScope.launch {
            _uiState.value = InputUiState.Saving
            runCatching {
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
        synchronized(readJobs) { readJobs.forEach { it.cancel() }; readJobs.clear() }
        _readingCount.value = 0
        _pendingInserts.value = emptyList()
        _readMessage.value = null
        _rawText.value = ""
        _selectedPrioritas.value = null
        _selectedStatus.value = NoteStatus.BELUM_MULAI
        _useAlarm.value = false
        _attachments.value = emptyList()
        _selectedGroups.value = emptyList()
        userTouchedPrioritas = false
        userTouchedStatus = false
        _uiState.value = InputUiState.Idle
    }
}
