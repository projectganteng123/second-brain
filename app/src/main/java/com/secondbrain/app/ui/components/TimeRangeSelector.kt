package com.secondbrain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.theme.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class RangePreset(val label: String) {
    TODAY("Hari ini"),
    WEEK("Minggu ini"),
    MONTH("Bulan ini"),
    CUSTOM("Custom")
}

/** Rentang tanggal terpilih (inklusif) untuk halaman Keuangan & Acara. */
data class TimeRange(val from: LocalDate, val to: LocalDate, val preset: RangePreset) {
    fun contains(d: LocalDate): Boolean = !d.isBefore(from) && !d.isAfter(to)

    fun label(): String = when (preset) {
        RangePreset.CUSTOM -> {
            val f = DateTimeFormatter.ofPattern("dd/MM")
            "${from.format(f)}–${to.format(f)}"
        }
        else -> preset.label
    }

    companion object {
        fun of(preset: RangePreset): TimeRange {
            val today = LocalDate.now()
            return when (preset) {
                RangePreset.TODAY -> TimeRange(today, today, preset)
                RangePreset.WEEK -> {
                    val monday = today.with(DayOfWeek.MONDAY)
                    TimeRange(monday, monday.plusDays(6), preset)
                }
                RangePreset.MONTH ->
                    TimeRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), preset)
                RangePreset.CUSTOM -> TimeRange(today.minusDays(6), today, preset)
            }
        }
    }
}

/** Dropdown rentang waktu di pojok kanan atas halaman; Custom membuka dialog dua tanggal. */
@Composable
fun TimeRangeSelector(
    range: TimeRange,
    onChange: (TimeRange) -> Unit,
    isDark: Boolean
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf(range.from.toString()) }
    var customTo by remember { mutableStateOf(range.to.toString()) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) GlassDark else GlassLight)
                .clickable { menuOpen = true }
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                range.label(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) Lavender200 else Lavender600
            )
            Icon(
                Icons.Outlined.ArrowDropDown, null,
                modifier = Modifier.size(18.dp),
                tint = if (isDark) Lavender400 else Gray400
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            RangePreset.entries.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label, style = MaterialTheme.typography.bodySmall) },
                    onClick = {
                        menuOpen = false
                        if (preset == RangePreset.CUSTOM) {
                            customFrom = range.from.toString()
                            customTo = range.to.toString()
                            showCustom = true
                        } else onChange(TimeRange.of(preset))
                    }
                )
            }
        }
    }

    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Rentang waktu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Dari", customFrom, { customFrom = it ?: "" }, isDark, Modifier.fillMaxWidth())
                    DateField("Sampai", customTo, { customTo = it ?: "" }, isDark, Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = runCatching { LocalDate.parse(customFrom) }.getOrNull()
                    val t = runCatching { LocalDate.parse(customTo) }.getOrNull()
                    if (f != null && t != null) {
                        onChange(if (t.isBefore(f)) TimeRange(t, f, RangePreset.CUSTOM)
                                 else TimeRange(f, t, RangePreset.CUSTOM))
                        showCustom = false
                    }
                }) { Text("Terapkan") }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Batal") } }
        )
    }
}
