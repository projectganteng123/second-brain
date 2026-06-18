package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.viewmodel.DashboardTab
import com.secondbrain.app.viewmodel.DashboardViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    repo: NoteRepository,
    onAddNote: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onAskClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isDark = isSystemDark()
    val notes by vm.filteredNotes.collectAsState()
    val tab by vm.selectedTab.collectAsState()
    val todayCount by vm.todayCount.collectAsState()
    val weekCount by vm.weekCount.collectAsState()
    val pendingCount by vm.pendingCount.collectAsState()

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(
        containerColor = bgColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddNote,
                shape = RoundedCornerShape(50),
                containerColor = Lavender600,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(0.dp)
            ) {
                Icon(Icons.Outlined.Add, "Tambah catatan")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            greeting(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDark) Lavender50 else Lavender800
                        )
                        Text(
                            todayFormatted(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Lavender400 else Gray600
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(
                            Icons.Outlined.AutoAwesome, "Tanya AI",
                            tint = if (isDark) Lavender400 else Lavender600,
                            modifier = Modifier.size(22.dp).clickable(onClick = onAskClick)
                        )
                        Icon(
                            Icons.Outlined.Search, "Cari",
                            tint = if (isDark) Lavender400 else Lavender600,
                            modifier = Modifier.size(22.dp).clickable(onClick = onSearchClick)
                        )
                        Icon(
                            Icons.Outlined.Settings, "Pengaturan",
                            tint = if (isDark) Lavender400 else Lavender600,
                            modifier = Modifier.size(22.dp).clickable(onClick = onSettingsClick)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // Summary strip
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryChip("$todayCount", "hari ini", Sky50, Sky200, Sky600, Modifier.weight(1f))
                    SummaryChip("$weekCount", "minggu ini", Mint50, Mint200, Mint600, Modifier.weight(1f))
                    SummaryChip("$pendingCount", "pending", Peach50, Peach200, Peach600, Modifier.weight(1f))
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            // Tabs
            item {
                TabRow(
                    tab = tab,
                    isDark = isDark,
                    onSelect = vm::selectTab
                )
            }

            // Section label
            item {
                SectionLabel(
                    when (tab) {
                        DashboardTab.HARI_INI    -> "${notes.size} kegiatan hari ini"
                        DashboardTab.MINGGU_INI  -> "${notes.size} kegiatan minggu ini"
                        DashboardTab.AKAN_DATANG -> "${notes.size} kegiatan mendatang"
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (notes.isEmpty()) {
                item {
                    EmptyState(tab)
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    val meta = repo.metadataFrom(note)
                    NoteCard(
                        title = meta?.title?.ifBlank { note.rawText.take(60) } ?: note.rawText.take(60),
                        type = meta?.type ?: NoteType.NOTE,
                        timeRange = buildTimeRange(meta),
                        prioritas = note.prioritas?.let { runCatching { Priority.valueOf(it) }.getOrNull() },
                        status = note.status?.let { runCatching { NoteStatus.valueOf(it) }.getOrNull() },
                        onClick = { onNoteClick(note.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SummaryChip(
    count: String, label: String,
    bg: Color, border: Color, textColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(count, style = MaterialTheme.typography.titleLarge, color = textColor)
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.75f))
    }
}

@Composable
private fun TabRow(
    tab: DashboardTab,
    isDark: Boolean,
    onSelect: (DashboardTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) GlassDark else GlassLight.copy(alpha = 0.7f))
            .border(1.dp, if (isDark) GlassBorderDark else GlassBorderLight, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DashboardTab.entries.forEach { t ->
            val selected = t == tab
            val label = when (t) {
                DashboardTab.HARI_INI    -> "Hari Ini"
                DashboardTab.MINGGU_INI  -> "Minggu"
                DashboardTab.AKAN_DATANG -> "Akan Datang"
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) (if (isDark) Lavender600.copy(0.4f) else Color.White)
                        else Color.Transparent
                    )
                    .clickable { onSelect(t) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) (if (isDark) Lavender200 else Lavender600)
                            else (if (isDark) Lavender400 else Gray400),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyState(tab: DashboardTab) {
    val isDark = isSystemDark()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.EventNote, null,
            tint = if (isDark) Lavender400 else Lavender200,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Tidak ada kegiatan",
            style = MaterialTheme.typography.titleSmall,
            color = if (isDark) Lavender400 else Gray400
        )
        Text(
            when (tab) {
                DashboardTab.HARI_INI    -> "Tambah catatan dengan jadwal hari ini"
                DashboardTab.MINGGU_INI  -> "Belum ada kegiatan minggu ini"
                DashboardTab.AKAN_DATANG -> "Belum ada kegiatan mendatang"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Lavender400.copy(0.6f) else Gray400.copy(0.7f),
            textAlign = TextAlign.Center
        )
    }
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 12 -> "Selamat pagi"
        hour < 15 -> "Selamat siang"
        hour < 18 -> "Selamat sore"
        else      -> "Selamat malam"
    }
}

private fun todayFormatted(): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale("id", "ID"))
    return LocalDate.now().format(formatter)
}

private fun buildTimeRange(meta: Metadata?): String? {
    if (meta == null) return null
    return when {
        meta.startTime != null && meta.endTime != null -> "${meta.startTime} – ${meta.endTime}"
        meta.startTime != null -> meta.startTime
        else -> null
    }
}
