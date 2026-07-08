package com.secondbrain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.theme.*

/** Chip satu grup. onRemove != null → ikon ✕ kecil di kanan (lepas dari grup). */
@Composable
fun GroupChip(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val bg = when {
        selected && isDark -> Lavender600.copy(alpha = 0.3f)
        selected           -> Lavender100
        isDark             -> GlassDark
        else               -> GlassLight
    }
    val border = when {
        selected && isDark -> Lavender400.copy(alpha = 0.5f)
        selected           -> Lavender400
        isDark             -> GlassBorderDark
        else               -> GlassBorderLight
    }
    val fg = when {
        selected && isDark -> Lavender200
        selected           -> Lavender600
        isDark             -> Lavender400
        else               -> Gray600
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = fg)
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Outlined.Close, "Lepas grup",
                tint = fg,
                modifier = Modifier.size(14.dp).clickable(onClick = onRemove)
            )
        }
    }
}

/**
 * Baris chip untuk MEMILIH grup catatan (dipakai Preview).
 * Chip = saran AI + pilihan manual; saran yang belum ada di DB berlabel "(baru)".
 * Chip "+ Grup" membuka picker (pilih existing / buat baru).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupPickerSection(
    selectedNames: List<String>,
    suggestions: List<String>,
    existingNames: List<String>,
    onToggle: (String) -> Unit
) {
    val isDark = isSystemDark()
    var showPicker by remember { mutableStateOf(false) }

    fun selected(name: String) = selectedNames.any { it.trim().equals(name.trim(), ignoreCase = true) }
    fun existing(name: String) = existingNames.any { it.trim().equals(name.trim(), ignoreCase = true) }

    val shown = (suggestions + selectedNames).distinctBy { it.trim().lowercase() }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        shown.forEach { name ->
            val label = if (existing(name)) name.trim() else "${name.trim()} (baru)"
            GroupChip(label, selected(name), isDark, onClick = { onToggle(name.trim()) })
        }
        GroupChip("+ Grup", selected = false, isDark = isDark, onClick = { showPicker = true })
    }
    if (shown.isEmpty()) {
        Text(
            "Tidak ada saran grup. Ketuk + Grup untuk memilih/membuat.",
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender400 else Gray400
        )
    }

    if (showPicker) {
        GroupPickerDialog(
            existingNames = existingNames,
            alreadySelected = selectedNames,
            onPick = { onToggle(it); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/** Dialog pilih grup existing atau buat grup baru (nama diketik). */
@Composable
fun GroupPickerDialog(
    existingNames: List<String>,
    alreadySelected: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isSystemDark()
    var newName by remember { mutableStateOf("") }
    val candidates = existingNames.filterNot { e ->
        alreadySelected.any { it.trim().equals(e.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pilih grup") },
        text = {
            Column {
                if (candidates.isEmpty()) {
                    Text("Belum ada grup lain.", style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray600)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(candidates) { name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(name) }
                                    .padding(vertical = 10.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Grup baru") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newName.isNotBlank()) onPick(newName.trim()) },
                enabled = newName.isNotBlank()
            ) { Text("Buat & pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
