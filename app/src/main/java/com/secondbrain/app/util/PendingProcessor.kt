package com.secondbrain.app.util

import com.secondbrain.app.ai.AIService
import com.secondbrain.app.ai.PromptTemplates
import com.secondbrain.app.data.repository.NoteRepository

/**
 * Memproses catatan yang tersimpan offline (isPendingExtraction=true) saat app dibuka
 * dan API key tersedia. Catatan yang gagal tetap pending untuk dicoba lagi nanti.
 */
object PendingProcessor {

    suspend fun processAll(repo: NoteRepository, prefs: PrefsManager) {
        if (!prefs.hasAnyActiveApiKey()) return

        val pending = repo.getPending()
        if (pending.isEmpty()) return

        DebugLog.log("Pending", "Memproses ${pending.size} catatan tertunda")
        val service = AIService.forExtraction(prefs)
        val offset = prefs.getAlarmOffsetMinutes()

        for (note in pending) {
            val now = PromptTemplates.nowString()
            service.extractMetadata(note.rawText, now, repo.activeGroupNames())
                .onSuccess { repo.updateMetadata(note.id, it, offset) }
                .onFailure { DebugLog.log("Pending ✕", "id=${note.id}: ${it.message}") }
        }
    }
}
