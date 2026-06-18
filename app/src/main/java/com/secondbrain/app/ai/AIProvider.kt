package com.secondbrain.app.ai

import com.secondbrain.app.data.model.Metadata

interface AIProvider {
    suspend fun extractMetadata(rawText: String, currentDateTime: String): Result<Metadata>
    suspend fun answerQuestion(question: String, contextNotes: List<String>): Result<String>
}

data class AIConfig(
    val apiKey: String,
    val model: String = "gemini-2.5-flash",
    /** Template ekstraksi kustom dari user. null = pakai PromptTemplates.DEFAULT_EXTRACTION */
    val extractionPromptTemplate: String? = null
)
