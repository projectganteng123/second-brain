package com.secondbrain.app.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Satu blok pertanyaan+jawaban di dalam teks catatan, mis. "[Belanja apa?: kopi]".
 * Sumber kebenaran batas blok = RANGE ini (bergeser saat teks diedit), BUKAN parsing '['/']'.
 */
data class CaptureBlock(
    val start: Int,     // indeks '['
    val labelEnd: Int,  // indeks awal jawaban (setelah "[Label: ")
    val end: Int        // indeks setelah ']'
)

sealed interface ButtonTarget {
    object Insert : ButtonTarget
    data class Replace(val block: CaptureBlock) : ButtonTarget
}

/**
 * Mengelola TextFieldValue + daftar blok. Logika murni agar mudah dinalar/uji.
 */
class GuidedCaptureController {

    var value by mutableStateOf(TextFieldValue(""))
        private set
    var blocks by mutableStateOf<List<CaptureBlock>>(emptyList())
        private set
    /** start blok yang sedang disorot (Pengaman 2) saat akan di-replace; null = tidak ada. */
    var highlightStart by mutableStateOf<Int?>(null)
        private set

    /** Edit manual oleh user: geser/putuskan blok mengikuti perubahan teks. */
    fun onValueChange(new: TextFieldValue) {
        if (new.text != value.text) {
            blocks = adjustBlocks(value.text, new.text, blocks)
        }
        value = new
    }

    /** Reset penuh (mis. setelah simpan). */
    fun clear() {
        value = TextFieldValue("")
        blocks = emptyList()
        highlightStart = null
    }

    fun setHighlight(start: Int?) { highlightStart = start }

    /** Tentukan apakah klik tombol akan MENGGANTI blok (kursor di dalam blok) atau MENAMBAH. */
    fun targetForButton(): ButtonTarget {
        val cursor = value.selection.start
        val b = blocks.firstOrNull { cursor > it.start && cursor < it.end }
        return if (b != null) ButtonTarget.Replace(b) else ButtonTarget.Insert
    }

    /** Sisipkan blok berlabel pada posisi kursor. Pengaman 1: kursor lompat ke ruang kosong setelah blok. */
    fun insertLabeledBlock(label: String, answer: String) {
        val text = value.text
        val pos = value.selection.start.coerceIn(0, text.length)
        val needLead = pos > 0 && text[pos - 1] != ' ' && text[pos - 1] != '\n'
        val lead = if (needLead) " " else ""
        val blockText = "[$label: $answer]"
        val trail = " "
        val insert = lead + blockText + trail
        val newText = text.substring(0, pos) + insert + text.substring(pos)

        val blockStart = pos + lead.length
        val newBlock = CaptureBlock(
            start = blockStart,
            labelEnd = blockStart + 1 + label.length + 2, // "[" + label + ": "
            end = blockStart + blockText.length
        )
        val shifted = blocks.map { if (it.start >= pos) it.shift(insert.length) else it }
        blocks = (shifted + newBlock).sortedBy { it.start }

        val cursor = newBlock.end + trail.length            // ruang kosong setelah blok
        value = TextFieldValue(newText, TextRange(cursor))
        highlightStart = null
    }

    /** Ganti isi sebuah blok. Jika answer kosong → hapus blok (jadi kosong). */
    fun replaceBlock(block: CaptureBlock, label: String, answer: String) {
        val text = value.text
        val safeStart = block.start.coerceIn(0, text.length)
        val safeEnd = block.end.coerceIn(safeStart, text.length)

        if (answer.isBlank()) {
            val newText = text.substring(0, safeStart) + text.substring(safeEnd)
            val removedLen = safeEnd - safeStart
            blocks = blocks.filter { it.start != block.start }
                .map { if (it.start >= safeEnd) it.shift(-removedLen) else it }
            value = TextFieldValue(newText, TextRange(safeStart))
            highlightStart = null
            return
        }

        val newBlockText = "[$label: $answer]"
        val newText = text.substring(0, safeStart) + newBlockText + text.substring(safeEnd)
        val delta = newBlockText.length - (safeEnd - safeStart)
        val newBlock = CaptureBlock(
            start = safeStart,
            labelEnd = safeStart + 1 + label.length + 2,
            end = safeStart + newBlockText.length
        )
        blocks = blocks.filter { it.start != block.start }
            .map { if (it.start >= safeEnd) it.shift(delta) else it }
            .plus(newBlock)
            .sortedBy { it.start }

        val cursor = newBlock.end + (if (newBlock.end < newText.length && newText[newBlock.end] == ' ') 1 else 0)
        value = TextFieldValue(newText, TextRange(cursor.coerceAtMost(newText.length)))
        highlightStart = null
    }

    /** Tambah teks bebas (mic besar tanpa template) di posisi kursor. */
    fun appendPlain(extra: String) {
        if (extra.isBlank()) return
        val text = value.text
        val pos = value.selection.start.coerceIn(0, text.length)
        val needLead = pos > 0 && text[pos - 1] != ' ' && text[pos - 1] != '\n'
        val insert = (if (needLead) " " else "") + extra
        val newText = text.substring(0, pos) + insert + text.substring(pos)
        blocks = blocks.map { if (it.start >= pos) it.shift(insert.length) else it }
        value = TextFieldValue(newText, TextRange(pos + insert.length))
    }

    private fun CaptureBlock.shift(d: Int) = copy(start = start + d, labelEnd = labelEnd + d, end = end + d)

    /** Geser/putuskan blok berdasarkan diff teks lama→baru. Blok yang pembatasnya kena edit = putus (dibuang). */
    private fun adjustBlocks(old: String, new: String, src: List<CaptureBlock>): List<CaptureBlock> {
        if (old == new || src.isEmpty()) return src
        val maxPrefix = minOf(old.length, new.length)
        var prefix = 0
        while (prefix < maxPrefix && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < maxPrefix - prefix &&
            old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val editStart = prefix
        val oldEditEnd = old.length - suffix
        val delta = new.length - old.length

        return src.mapNotNull { b ->
            when {
                oldEditEnd <= b.start -> b.shift(delta)                       // edit sebelum blok
                editStart >= b.end -> b                                       // edit sesudah blok
                editStart >= b.labelEnd && oldEditEnd <= b.end - 1 ->         // di dalam jawaban (tak menyentuh ']')
                    b.copy(end = b.end + delta)
                else -> null                                                 // pembatas/label kena → blok rusak
            }
        }
    }
}
