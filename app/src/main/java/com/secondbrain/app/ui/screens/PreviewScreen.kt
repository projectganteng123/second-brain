package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.notification.ReminderWorker
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.viewmodel.InputUiState
import com.secondbrain.app.viewmodel.InputViewModel

@Composable
fun PreviewScreen(
    vm: InputViewModel,
    metadata: Metadata,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val isDark = isSystemDark()
    val uiState by vm.uiState.collectAsState()
    val selectedPrioritas by vm.selectedPrioritas.collectAsState()
    val selectedStatus by vm.selectedStatus.collectAsState()
    val useAlarm by vm.useAlarm.collectAsState()

    var editedMetadata by remember { mutableStateOf(metadata) }
    var editing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(uiState) {
        if (uiState is InputUiState.Saved) {
            ReminderWorker.enqueueNow(context)
            onSaved()
            vm.reset()
        }
    }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Outlined.ArrowBack, "Kembali",
                        tint = if (isDark) Lavender200 else Lavender600
                    )
                }
                Text(
                    "Cek & simpan",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(12.dp))

            // Metadata card
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("hasil ekstraksi AI")
                    TextButton(onClick = { editing = !editing }) {
                        Icon(
                            if (editing) Icons.Outlined.Close else Icons.Outlined.Edit,
                            null, modifier = Modifier.size(16.dp), tint = Lavender600
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (editing) "Tutup" else "Edit", color = Lavender600,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }

                MetadataRow("Judul", editedMetadata.title.ifBlank { "-" })
                MetadataRow("Jenis", editedMetadata.type.label)

                if (editedMetadata.startTime != null || editedMetadata.endTime != null) {
                    val time = buildString {
                        editedMetadata.startTime?.let { append(it) }
                        editedMetadata.endTime?.let { append(" – $it") }
                    }
                    MetadataRow("Waktu", time)
                }

                if (editedMetadata.recurrenceDates.isNotEmpty()) {
                    MetadataRow(
                        "Tanggal",
                        if (editedMetadata.recurrenceDates.size == 1)
                            editedMetadata.recurrenceDates.first()
                        else
                            "${editedMetadata.recurrenceDates.first()} (+${editedMetadata.recurrenceDates.size - 1})"
                    )
                }

                if (editedMetadata.locations.isNotEmpty()) {
                    MetadataRow("Lokasi", editedMetadata.locations.joinToString(", ") { it.value })
                }

                if (editedMetadata.entities.people.isNotEmpty()) {
                    MetadataRow("Orang", editedMetadata.entities.people.joinToString(", "))
                }

                if (editedMetadata.entities.organizations.isNotEmpty()) {
                    MetadataRow("Organisasi", editedMetadata.entities.organizations.joinToString(", "))
                }

                if (editedMetadata.keywords.isNotEmpty()) {
                    MetadataRow("Keywords", editedMetadata.keywords.take(5).joinToString(", "))
                }

                if (editedMetadata.actions.isNotEmpty()) {
                    MetadataRow(
                        "Aksi",
                        editedMetadata.actions.joinToString("\n") {
                            buildString {
                                append("• ${it.action}")
                                it.owner?.let { o -> append(" ($o)") }
                                it.deadline?.let { d -> append(" → $d") }
                            }
                        }
                    )
                }

                if (editedMetadata.summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        editedMetadata.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender200 else Gray600,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // Editable card
            if (editing) {
                Spacer(Modifier.height(8.dp))
                MetadataEditor(
                    metadata = editedMetadata,
                    isDark = isDark,
                    onChange = { editedMetadata = it }
                )
            }

            // Warning: no end time
            if (editedMetadata.endTime == null && editedMetadata.startTime != null) {
                Spacer(Modifier.height(8.dp))
                WarningBox("Waktu selesai tidak ditemukan. Reminder akan dikirim saat waktu mulai.")
            }

            // Warning: no dates
            if (editedMetadata.recurrenceDates.isEmpty() &&
                editedMetadata.type in listOf(NoteType.MEETING, NoteType.TASK, NoteType.EVENT, NoteType.REMINDER)
            ) {
                Spacer(Modifier.height(8.dp))
                WarningBox("Tanggal tidak terdeteksi. Catatan tidak akan muncul di kalender.")
            }

            Spacer(Modifier.height(12.dp))

            // Manual fields
            GlassCard {
                SectionLabel("pengaturan manual", modifier = Modifier.padding(bottom = 8.dp))

                // Prioritas
                Text(
                    "Prioritas",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(4.dp))
                PrioritySelector(selectedPrioritas, vm::setPrioritas)

                Spacer(Modifier.height(4.dp))
                Text(
                    "Dibiarkan = pakai rekomendasi AI.",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray400
                )

                Spacer(Modifier.height(10.dp))

                // Status
                Text(
                    "Status",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(4.dp))
                StatusSelector(selectedStatus, vm::setStatus)

                Spacer(Modifier.height(12.dp))

                // Alarm toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Jadikan alarm", style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Lavender50 else Lavender800)
                        Text("Pengingat berbunyi alarm + layar penuh, bukan sekadar notifikasi",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Lavender400 else Gray600)
                    }
                    Switch(checked = useAlarm, onCheckedChange = vm::setUseAlarm)
                }

                if (editedMetadata.preparationTime != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(14.dp), tint = Mint600)
                        Text("Pengingat persiapan: ${editedMetadata.preparationTime}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Mint200 else Mint600)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Save button
            val isSaving = uiState is InputUiState.Saving
            GlassButton(
                text = if (isSaving) "Menyimpan..." else "Simpan catatan",
                icon = Icons.Outlined.Save,
                onClick = { vm.saveNote(editedMetadata) },
                accent = true,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun WarningBox(message: String) {
    val isDark = isSystemDark()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.Warning, null, tint = Lemon600, modifier = Modifier.size(16.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Lemon200 else Lemon800
        )
    }
}

@Composable
private fun PrioritySelector(selected: Priority?, onSelect: (Priority?) -> Unit) {
    val isDark = isSystemDark()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Priority.entries.forEach { p ->
            val isSelected = p == selected
            GlassButton(
                text = p.label,
                onClick = { onSelect(if (isSelected) null else p) },
                accent = isSelected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusSelector(selected: NoteStatus?, onSelect: (NoteStatus?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NoteStatus.entries.forEach { s ->
            GlassButton(
                text = s.label,
                onClick = { onSelect(s) },
                accent = s == selected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MetadataEditor(
    metadata: Metadata,
    isDark: Boolean,
    onChange: (Metadata) -> Unit
) {
    // recurrenceDates sebagai teks dipisah koma
    var datesText by remember(metadata.recurrenceDates) {
        mutableStateOf(metadata.recurrenceDates.joinToString(", "))
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Lavender400,
        unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
        focusedContainerColor = if (isDark) GlassDark else GlassLight,
        unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
        focusedTextColor = if (isDark) Lavender50 else Lavender800,
        unfocusedTextColor = if (isDark) Lavender50 else Lavender800
    )

    GlassCard {
        SectionLabel("edit manual", modifier = Modifier.padding(bottom = 8.dp))

        OutlinedTextField(
            value = metadata.title,
            onValueChange = { onChange(metadata.copy(title = it)) },
            label = { Text("Judul") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = fieldColors
        )

        Spacer(Modifier.height(8.dp))
        Text("Jenis", style = MaterialTheme.typography.labelSmall, color = if (isDark) Lavender400 else Gray600)
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NoteType.entries.forEach { t ->
                val sel = t == metadata.type
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (sel) (if (isDark) Lavender600.copy(0.4f) else Lavender100)
                            else (if (isDark) GlassDark else GlassLight)
                        )
                        .clickable { onChange(metadata.copy(type = t)) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sel) (if (isDark) Lavender200 else Lavender600)
                                else (if (isDark) Lavender400 else Gray600)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = metadata.startTime ?: "",
                onValueChange = { onChange(metadata.copy(startTime = it.ifBlank { null })) },
                label = { Text("Mulai (HH:mm)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = fieldColors
            )
            OutlinedTextField(
                value = metadata.endTime ?: "",
                onValueChange = { onChange(metadata.copy(endTime = it.ifBlank { null })) },
                label = { Text("Selesai (HH:mm)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = fieldColors
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = datesText,
            onValueChange = { text ->
                datesText = text
                val parsed = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                onChange(metadata.copy(recurrenceDates = parsed))
            },
            label = { Text("Tanggal (YYYY-MM-DD, pisah koma)") },
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = fieldColors
        )
        Text(
            "Contoh: 2026-06-20, 2026-06-27",
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender400 else Gray400,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = metadata.preparationTime ?: "",
            onValueChange = { onChange(metadata.copy(preparationTime = it.ifBlank { null })) },
            label = { Text("Waktu persiapan (YYYY-MM-DDTHH:mm)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = fieldColors
        )
        Text(
            "Pengingat persiapan. Contoh: 2026-06-23T19:00 (kosongkan jika tak perlu)",
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender400 else Gray400,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
