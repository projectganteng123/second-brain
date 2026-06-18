package com.secondbrain.app.util

import com.secondbrain.app.ai.AIService
import com.secondbrain.app.data.repository.NoteRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Memproses catatan yang tersimpan offline (isPendingExtraction=true) saat app dibuka
 * dan API key tersedia. Catatan yang gagal tetap pending untuk dicoba lagi nanti.
 */
object PendingProcessor {

    suspend fun processAll(repo: NoteRepository, prefs: PrefsManager) {
        val keys = prefs.getApiKeys()
        if (keys.isEmpty()) return

        val pending = repo.getPending()
        if (pending.isEmpty()) return

        DebugLog.log("Pending", "Memproses ${pending.size} catatan tertunda")
        val service = AIService(
            keys = keys,
            preferredModel = prefs.getModel(),
            modelPool = PrefsManager.MODEL_OPTIONS,
            promptTemplate = prefs.getCustomPrompt().ifBlank { null }
        )
        val offset = prefs.getReminderOffsetHours()

        for (note in pending) {
            val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            service.extractMetadata(note.rawText, now)
                .onSuccess { repo.updateMetadata(note.id, it, offset) }
                .onFailure { DebugLog.log("Pending ✕", "id=${note.id}: ${it.message}") }
        }
    }
}
