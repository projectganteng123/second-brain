package com.secondbrain.app.ui.screens

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
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*

@OptIn(FlowPreview::class)
@Composable
fun SearchScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    var query by remember { mutableStateOf("") }

    val results by remember(query) {
        if (query.length < 2) flowOf(emptyList())
        else repo.search(query).debounce(300)
    }.collectAsState(initial = emptyList())

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari catatan...") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.Search, null, tint = if (isDark) Lavender400 else Lavender600)
                    },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Outlined.Close, "Hapus", tint = Gray400)
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Lavender400,
                        unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                        focusedContainerColor = if (isDark) GlassDark else GlassLight,
                        unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
                        focusedTextColor = if (isDark) Lavender50 else Lavender800,
                        unfocusedTextColor = if (isDark) Lavender50 else Lavender800
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            if (query.length < 2) {
                Text(
                    "Ketik minimal 2 karakter untuk mencari",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray400,
                    modifier = Modifier.padding(top = 40.dp).fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                SectionLabel("${results.size} hasil untuk \"$query\"")
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { note ->
                        val meta = repo.metadataFrom(note)
                        NoteCard(
                            title = meta?.title?.ifBlank { note.rawText.take(60) } ?: note.rawText.take(60),
                            type = meta?.type ?: NoteType.NOTE,
                            timeRange = null,
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
