package com.secondbrain.app.capture

/** Template pertanyaan bawaan. Editor custom = nanti (YAGNI). */
data class CaptureTemplate(
    val id: String,
    val name: String,
    val questions: List<String>
)

object CaptureTemplates {
    val BUILT_IN = listOf(
        CaptureTemplate(
            id = "pengeluaran",
            name = "Pengeluaran",
            questions = listOf("Belanja apa?", "Berapa harganya?", "Kenapa belanja itu?")
        ),
        CaptureTemplate(
            id = "ide",
            name = "Ide",
            questions = listOf("Apa idenya?", "Kenapa penting?", "Langkah pertama?")
        ),
        CaptureTemplate(
            id = "tugas",
            name = "Tugas",
            questions = listOf("Apa tugasnya?", "Kapan deadline?", "Siapa terlibat?")
        )
    )

    fun byId(id: String?): CaptureTemplate? = BUILT_IN.firstOrNull { it.id == id }
}
