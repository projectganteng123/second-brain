package com.secondbrain.app.util

/**
 * Parser nominal toleran gaya Indonesia untuk field angka di editor metadata.
 * Menerima "30000", "30.000", "30.000,50", "30,5", "Rp 30.000".
 * Return null bila tidak bisa diparse — pemanggil TIDAK boleh menimpa nilai lama diam-diam.
 */
object AmountParse {

    fun parse(raw: String): Double? {
        var s = raw.trim()
            .replace(Regex("(?i)^rp\\.?\\s*"), "")
            .replace(" ", "")
        if (s.isEmpty()) return null
        val hasDot = '.' in s
        val hasComma = ',' in s
        s = when {
            hasDot && hasComma ->
                // Pemisah yang muncul terakhir dianggap desimal, satunya ribuan.
                if (s.lastIndexOf(',') > s.lastIndexOf('.'))
                    s.replace(".", "").replace(',', '.')
                else
                    s.replace(",", "")
            hasComma -> s.replace(',', '.')
            hasDot -> {
                // Titik = ribuan bila semua grup setelah yang pertama 3 digit ("1.234.567"),
                // selain itu desimal ("30.5").
                val parts = s.split('.')
                if (parts.size > 1 && parts[0].isNotEmpty() && parts.drop(1).all { it.length == 3 })
                    s.replace(".", "")
                else s
            }
            else -> s
        }
        return s.toDoubleOrNull()?.takeIf { it >= 0 }
    }

    /** Tampilan awal di text field: tanpa ,0 untuk bilangan bulat. */
    fun format(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
