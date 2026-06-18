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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*

@Composable
fun ActionItemsScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onNoteClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val notes by repo.getAllActive().collectAsState(initial = emptyList())
    var hideDone by remember { mutableStateOf(false) }

    val actions = remember(notes, hideDone) {
        repo.allActionItems(notes).filter { !hideDone || !it.done }
    }

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

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
                        "Action items",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDark) Lavender50 else Lavender800
                    )
                }
                FilterChip(
                    selected = hideDone,
                    onClick = { hideDone = !hideDone },
                    label = { Text("Sembunyikan selesai", style = MaterialTheme.typography.labelSmall) }
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("${actions.size} tindakan")

            if (actions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Belum ada action item. Catatan dengan kata kerja (kirim, follow up, dll) akan muncul di sini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray400,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(actions) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) GlassDark else GlassLight)
                                .border(1.dp, if (isDark) GlassBorderDark else GlassBorderLight, RoundedCornerShape(12.dp))
                                .clickable { onNoteClick(item.noteId) }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    if (item.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    null, modifier = Modifier.size(16.dp),
                                    tint = if (item.done) Mint600 else (if (isDark) Lavender400 else Gray400)
                                )
                                Text(
                                    item.action,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) Lavender50 else Lavender800,
                                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(start = 24.dp)) {
                                item.owner?.let {
                                    Label(Icons.Outlined.Person, it, isDark)
                                }
                                item.deadline?.let {
                                    Label(Icons.Outlined.Event, it, isDark)
                                }
                                Label(Icons.Outlined.Description, item.noteTitle, isDark)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Label(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, isDark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, modifier = Modifier.size(12.dp), tint = if (isDark) Lavender400 else Gray400)
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (isDark) Lavender400 else Gray600, maxLines = 1)
    }
}
