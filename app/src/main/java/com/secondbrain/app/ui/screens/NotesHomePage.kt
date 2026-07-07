package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import java.time.LocalDate

private enum class DateFilter(val label: String) {
    SEMUA("Semua"), TERJADWAL("Terjadwal"), TANPA_TANGGAL("Tanpa tanggal")
}

private enum class NoteSort(val label: String) {
    TERBARU("Terbaru dibuat"),
    TERLAMA("Terlama dibuat"),
    JADWAL("Jadwal terdekat")
}

/**
 * Halaman kiri dari layar Input (muncul lewat swipe ke kanan / tombol hamburger):
 * semua catatan urut waktu — bisa dicari, difilter, dan diurutkan. Baris atas berisi
 * pintasan ke halaman Acara, Keuangan, Tanya AI, dan Pengaturan.
 * Geser ke kiri untuk kembali ke layar Input.
 */
@Composable
fun NotesHomePage(
    repo: NoteRepository,
    onNoteClick: (Long) -> Unit,
    onOpenEvents: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenQa: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackToInput: () -> Unit
) {
    val isDark = isSystemDark()
    val notes by repo.getAllActive().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var dateFilter by remember { mutableStateOf(DateFilter.SEMUA) }
    var typeFilter by remember { mutableStateOf<NoteType?>(null) }
    var sort by remember { mutableStateOf(NoteSort.TERBARU) }

    val shown = remember(notes, query, dateFilter, typeFilter, sort) {
        val q = query.trim().lowercase()
        notes.mapNotNull { note ->
            val meta = repo.metadataFrom(note)
            val hasDate = meta?.recurrenceDates?.isNotEmpty() == true
            val dateOk = when (dateFilter) {
                DateFilter.SEMUA -> true
                DateFilter.TERJADWAL -> hasDate
                DateFilter.TANPA_TANGGAL -> !hasDate
            }
            val typeOk = typeFilter == null || meta?.type == typeFilter
            val searchOk = q.isEmpty() || buildString {
                append(note.rawText.lowercase()); append(' ')
                meta?.let {
                    append(it.title.lowercase()); append(' ')
                    append(it.summary.lowercase()); append(' ')
                    append(it.keywords.joinToString(" ").lowercase())
                }
            }.contains(q)
            if (dateOk && typeOk && searchOk) note to meta else null
        }.sortedWith(
            when (sort) {
                NoteSort.TERBARU -> compareByDescending { it.first.createdAt }
                NoteSort.TERLAMA -> compareBy { it.first.createdAt }
                NoteSort.JADWAL -> compareBy {
                    it.second?.recurrenceDates?.mapNotNull { d ->
                        runCatching { LocalDate.parse(d) }.getOrNull()
                    }?.minOrNull() ?: LocalDate.MAX
                }
            }
        )
    }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Baris atas: judul + pintasan halaman lain + kembali ke input
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Catatan",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Timeline, "Halaman acara",
                        tint = if (isDark) Lavender400 else Lavender600,
                        modifier = Modifier.size(22.dp).clickable(onClick = onOpenEvents))
                    Icon(Icons.Outlined.Payments, "Halaman keuangan",
                        tint = if (isDark) Lavender400 else Lavender600,
                        modifier = Modifier.size(22.dp).clickable(onClick = onOpenFinance))
                    Icon(Icons.Outlined.AutoAwesome, "Tanya AI",
                        tint = if (isDark) Lavender400 else Lavender600,
                        modifier = Modifier.size(22.dp).clickable(onClick = onOpenQa))
                    Icon(Icons.Outlined.Settings, "Pengaturan",
                        tint = if (isDark) Lavender400 else Lavender600,
                        modifier = Modifier.size(22.dp).clickable(onClick = onOpenSettings))
                    Icon(Icons.Outlined.ChevronRight, "Kembali menulis",
                        tint = if (isDark) Lavender200 else Lavender600,
                        modifier = Modifier.size(22.dp).clickable(onClick = onBackToInput))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Pencarian
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari catatan…", color = if (isDark) Lavender400.copy(0.5f) else Gray400) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp),
                        tint = if (isDark) Lavender400 else Gray400)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, "Hapus pencarian", modifier = Modifier.size(16.dp),
                                tint = if (isDark) Lavender400 else Gray400)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Lavender400,
                    unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                    focusedContainerColor = if (isDark) GlassDark else GlassLight,
                    unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
                    focusedTextColor = if (isDark) Lavender50 else Lavender800,
                    unfocusedTextColor = if (isDark) Lavender50 else Lavender800
                )
            )

            Spacer(Modifier.height(8.dp))

            // Filter tanggal + sort
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    DateFilter.entries.forEach { f ->
                        HomeChip(f.label, f == dateFilter, isDark) { dateFilter = f }
                    }
                }
                SortMenu(sort, isDark) { sort = it }
            }

            Spacer(Modifier.height(6.dp))

            // Filter jenis
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HomeChip("Semua jenis", typeFilter == null, isDark) { typeFilter = null }
                NoteType.entries.forEach { t ->
                    HomeChip(t.label, typeFilter == t, isDark) {
                        typeFilter = if (typeFilter == t) null else t
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("${shown.size} catatan")
            Spacer(Modifier.height(4.dp))

            if (shown.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isNotBlank()) "Tidak ada catatan yang cocok dengan \"$query\""
                        else "Belum ada catatan — geser ke kiri untuk mulai menulis",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray400,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.first.id }) { (note, meta) ->
                        NoteCard(
                            title = meta?.title?.ifBlank { note.rawText.take(60) } ?: note.rawText.take(60),
                            type = meta?.type ?: NoteType.NOTE,
                            timeRange = meta?.recurrenceDates?.firstOrNull()?.let {
                                com.secondbrain.app.util.TimeFormat.dateMedium(it)
                            },
                            prioritas = note.prioritas?.let { runCatching { Priority.valueOf(it) }.getOrNull() },
                            status = note.status?.let { runCatching { NoteStatus.valueOf(it) }.getOrNull() },
                            onClick = { onNoteClick(note.id) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(sort: NoteSort, isDark: Boolean, onSelect: (NoteSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Outlined.SwapVert, "Urutkan",
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) GlassDark else GlassLight)
                .clickable { open = true }
                .padding(6.dp)
                .size(16.dp),
            tint = if (isDark) Lavender200 else Lavender600
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            NoteSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(if (s == sort) "✓ ${s.label}" else s.label,
                            style = MaterialTheme.typography.bodySmall)
                    },
                    onClick = { onSelect(s); open = false }
                )
            }
        }
    }
}

@Composable
private fun HomeChip(label: String, sel: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (sel) (if (isDark) Lavender600.copy(0.4f) else Lavender100)
                else (if (isDark) GlassDark else GlassLight)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (sel) (if (isDark) Lavender200 else Lavender600)
                    else (if (isDark) Lavender400 else Gray600)
        )
    }
}
