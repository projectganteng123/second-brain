package com.secondbrain.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.database.GroupWithCount
import com.secondbrain.app.data.model.GroupEntity
import com.secondbrain.app.data.repository.NoteRepository
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import kotlinx.coroutines.launch

/** Daftar grup aktif + jumlah catatan; buat/rename/arsip/hapus grup. */
@Composable
fun GroupsScreen(
    repo: NoteRepository,
    onBack: () -> Unit,
    onGroupClick: (Long) -> Unit
) {
    val isDark = isSystemDark()
    val groups by repo.activeGroupsWithCount().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<GroupEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<GroupWithCount?>(null) }

    Scaffold(
        containerColor = if (isDark) Lavender900 else Gray50,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali",
                        tint = if (isDark) Lavender200 else Lavender600)
                }
                Text("Grup", style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800)
                Spacer(Modifier.weight(1f))
                GlassButton("Grup baru", onClick = { showCreate = true },
                    icon = Icons.Outlined.Add, accent = true)
            }
            Spacer(Modifier.height(12.dp))

            if (groups.isEmpty()) {
                Text(
                    "Belum ada grup. Buat lewat tombol di atas, atau centang saran AI saat menyimpan catatan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { it.group.id }) { g ->
                    GlassCard(modifier = Modifier.fillMaxWidth().clickable { onGroupClick(g.group.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(g.group.name, style = MaterialTheme.typography.bodyMedium,
                                    color = if (isDark) Lavender50 else Lavender800)
                                Text("${g.noteCount} catatan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Lavender400 else Gray600)
                            }
                            IconButton(onClick = { renameTarget = g.group }) {
                                Icon(Icons.Outlined.Edit, "Ganti nama",
                                    tint = if (isDark) Lavender400 else Lavender600,
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    repo.setGroupArchived(g.group.id, true)
                                    snackbar.showSnackbar("Grup \"${g.group.name}\" diarsipkan")
                                }
                            }) {
                                Icon(Icons.Outlined.Archive, "Arsipkan",
                                    tint = if (isDark) Lavender400 else Lavender600,
                                    modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { deleteTarget = g }) {
                                Icon(Icons.Outlined.DeleteOutline, "Hapus", tint = Rose600,
                                    modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        GroupNameDialog(
            title = "Grup baru",
            initial = "",
            onConfirm = { name ->
                scope.launch {
                    repo.resolveOrCreateGroup(name)
                    showCreate = false
                }
            },
            onDismiss = { showCreate = false }
        )
    }

    renameTarget?.let { target ->
        GroupNameDialog(
            title = "Ganti nama grup",
            initial = target.name,
            onConfirm = { name ->
                scope.launch {
                    val ok = repo.renameGroup(target.id, name)
                    if (!ok) snackbar.showSnackbar("Nama \"$name\" sudah dipakai grup lain")
                    renameTarget = null
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus grup \"${target.group.name}\"?") },
            text = { Text("${target.noteCount} catatan akan dikeluarkan dari grup ini. Catatannya sendiri TIDAK dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repo.deleteGroup(target.group.id)
                        deleteTarget = null
                    }
                }) { Text("Hapus", color = Rose600) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } }
        )
    }
}

/** Dialog input nama grup (buat baru / ganti nama). */
@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama grup") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Simpan")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
