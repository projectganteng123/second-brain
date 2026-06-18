package com.secondbrain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.viewmodel.NoteDetailViewModel

@Composable
fun NoteDetailScreen(
    vm: NoteDetailViewModel,
    noteId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val isDark = isSystemDark()
    val state by vm.state.collectAsState()

    LaunchedEffect(noteId) { vm.load(noteId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onDeleted() }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    var editing by remember { mutableStateOf(false) }
    var editText by remember(state.note?.id) { mutableStateOf(state.note?.rawText ?: "") }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(
        containerColor = bgColor,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val note = state.note
        val meta = state.metadata

        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Lavender600)
            }
            return@Scaffold
        }
        if (note == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Catatan tidak ditemukan", color = if (isDark) Lavender400 else Gray600)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                    }
                    Text(
                        "Detail catatan",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDark) Lavender50 else Lavender800
                    )
                }
                Row {
                    IconButton(onClick = { vm.toggleArchive() }) {
                        Icon(
                            if (note.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            "Arsip", tint = if (isDark) Lavender400 else Lavender600
                        )
                    }
                    IconButton(onClick = { vm.delete() }) {
                        Icon(Icons.Outlined.DeleteOutline, "Hapus", tint = Rose600)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Raw text card (view / edit)
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("catatan asli")
                    if (!editing) {
                        TextButton(onClick = { editText = note.rawText; editing = true }) {
                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(16.dp), tint = Lavender600)
                            Spacer(Modifier.width(4.dp))
                            Text("Edit", color = Lavender600, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (editing) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Lavender400,
                            unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                            focusedTextColor = if (isDark) Lavender50 else Lavender800,
                            unfocusedTextColor = if (isDark) Lavender50 else Lavender800
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = if (state.reExtracting) "Memproses..." else "Simpan & proses ulang",
                            icon = Icons.Outlined.AutoAwesome,
                            onClick = { vm.reExtract(editText); editing = false },
                            accent = true,
                            enabled = !state.reExtracting,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(text = "Batal", onClick = { editing = false })
                    }
                } else {
                    Text(
                        note.rawText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) Lavender50 else Lavender800
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Metadata
            if (meta != null) {
                GlassCard {
                    SectionLabel("metadata", modifier = Modifier.padding(bottom = 6.dp))
                    MetadataRow("Judul", meta.title.ifBlank { "-" })
                    MetadataRow("Jenis", meta.type.label)
                    if (meta.startTime != null || meta.endTime != null) {
                        MetadataRow("Waktu", buildString {
                            meta.startTime?.let { append(it) }
                            meta.endTime?.let { append(" – $it") }
                        })
                    }
                    if (meta.recurrenceDates.isNotEmpty()) {
                        MetadataRow("Tanggal", meta.recurrenceDates.joinToString(", "))
                    }
                    if (meta.locations.isNotEmpty()) {
                        MetadataRow("Lokasi", meta.locations.joinToString(", ") { it.value })
                    }
                    if (meta.entities.people.isNotEmpty()) {
                        MetadataRow("Orang", meta.entities.people.joinToString(", "))
                    }
                    if (meta.entities.organizations.isNotEmpty()) {
                        MetadataRow("Organisasi", meta.entities.organizations.joinToString(", "))
                    }
                    if (meta.keywords.isNotEmpty()) {
                        MetadataRow("Keywords", meta.keywords.joinToString(", "))
                    }
                    if (meta.actions.isNotEmpty()) {
                        MetadataRow("Aksi", meta.actions.joinToString("\n") {
                            buildString {
                                append("• ${it.action}")
                                it.owner?.let { o -> append(" ($o)") }
                                it.deadline?.let { d -> append(" → $d") }
                            }
                        })
                    }
                    if (meta.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            meta.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Lavender200 else Gray600,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Management: priority & status
            GlassCard {
                SectionLabel("manajemen", modifier = Modifier.padding(bottom = 8.dp))

                Text("Prioritas", style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600)
                Spacer(Modifier.height(4.dp))
                val curP = note.prioritas?.let { runCatching { Priority.valueOf(it) }.getOrNull() }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Priority.entries.forEach { p ->
                        GlassButton(
                            text = p.label,
                            onClick = { vm.setPrioritas(if (curP == p) null else p) },
                            accent = curP == p,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Status", style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600)
                Spacer(Modifier.height(4.dp))
                val curS = note.status?.let { runCatching { NoteStatus.valueOf(it) }.getOrNull() }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NoteStatus.entries.forEach { s ->
                        GlassButton(
                            text = s.label,
                            onClick = { vm.setStatus(if (curS == s) null else s) },
                            accent = curS == s,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jadikan alarm", style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Lavender50 else Lavender800)
                        Text("Pengingat berbunyi alarm + layar penuh",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender400 else Gray600)
                    }
                    Switch(checked = note.useAlarm, onCheckedChange = { vm.setUseAlarm(it) })
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
