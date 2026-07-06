package com.secondbrain.app.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Format waktu ramah-baca Indonesia untuk semua yang ditampilkan ke pengguna.
 *  String masukan yang tidak bisa diparse dikembalikan apa adanya (tidak crash). */
object TimeFormat {
    private val LOCALE = Locale("id", "ID")
    private val FULL = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", LOCALE)
    private val MEDIUM = DateTimeFormatter.ofPattern("d MMM yyyy", LOCALE)
    private val DATE_TIME = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH.mm", LOCALE)
    private val ISO_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

    fun date(d: LocalDate): String = d.format(FULL)
    fun dateMedium(d: LocalDate): String = d.format(MEDIUM)

    /** "2026-07-06" → "Sen, 6 Jul 2026" */
    fun date(iso: String?): String =
        iso?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.format(FULL) ?: it } ?: ""

    /** "2026-07-06" → "6 Jul 2026" */
    fun dateMedium(iso: String?): String =
        iso?.let { runCatching { LocalDate.parse(it) }.getOrNull()?.format(MEDIUM) ?: it } ?: ""

    /** "2026-07-06T14:30" → "Sen, 6 Jul 2026 14.30" */
    fun dateTime(isoT: String?): String =
        isoT?.let {
            runCatching { LocalDateTime.parse(it, ISO_DT) }.getOrNull()?.format(DATE_TIME) ?: it
        } ?: ""
}
