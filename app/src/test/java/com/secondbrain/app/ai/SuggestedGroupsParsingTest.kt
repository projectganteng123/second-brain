package com.secondbrain.app.ai

import com.secondbrain.app.data.GsonProvider
import com.secondbrain.app.data.model.Metadata
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuggestedGroupsParsingTest {

    private val financeKosong = """{"transactions":[]}"""
    private val scheduleKosong = """{"schedules":[]}"""

    @Test
    fun parseSuggestedGroups_trimDanDedupCaseInsensitive() {
        val universal = """
            {"title":"t","type":"note","summary":"s",
             "suggestedGroups":[" Renovasi Rumah ", "renovasi rumah", "Ide Bisnis", ""]}
        """.trimIndent()
        val meta = ExtractionParser.merge(universal, financeKosong, scheduleKosong)
        assertEquals(listOf("Renovasi Rumah", "Ide Bisnis"), meta.suggestedGroups)
    }

    @Test
    fun fieldAbsen_jadiNull() {
        val universal = """{"title":"t","type":"note","summary":"s"}"""
        val meta = ExtractionParser.merge(universal, financeKosong, scheduleKosong)
        assertNull(meta.suggestedGroups)
    }

    @Test
    fun arrayKosong_jadiNull() {
        val universal = """{"title":"t","type":"note","summary":"s","suggestedGroups":[]}"""
        val meta = ExtractionParser.merge(universal, financeKosong, scheduleKosong)
        assertNull(meta.suggestedGroups)
    }

    @Test
    fun metadataJsonLama_tanpaField_amanDibaca() {
        // metadataJson catatan lama (pra-fitur grup) tidak memuat suggestedGroups
        val lama = """{"title":"lama","type":"NOTE","summary":"","keywords":[]}"""
        val meta = GsonProvider.gson.fromJson(lama, Metadata::class.java)
        assertNull(meta.suggestedGroups)
    }

    @Test
    fun fillMenyuntikkanDaftarGrup() {
        val hasil = PromptTemplates.fill(
            "DAFTAR: {groups} CATATAN: {note}", "now", "isi",
            listOf("Renovasi Rumah", "Ide Bisnis")
        )
        assertTrue(hasil.contains("""["Renovasi Rumah","Ide Bisnis"]"""))
        assertFalse(hasil.contains("{groups}"))
    }

    @Test
    fun fillTanpaGrup_placeholderJadiArrayKosong() {
        val hasil = PromptTemplates.fill("DAFTAR: {groups}", "now", "isi")
        assertTrue(hasil.contains("DAFTAR: []"))
    }
}
