package com.secondbrain.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondbrain.app.ui.components.isSystemDark
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.util.DebugLog

@Composable
fun DebugScreen(onBack: () -> Unit) {
    val isDark = isSystemDark()
    val entries by DebugLog.entries.collectAsState()

    val bgColor = if (isDark) Lavender900 else Gray50

    Scaffold(containerColor = bgColor) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                    }
                    Text("Debug log", style = MaterialTheme.typography.titleMedium,
                        color = if (isDark) Lavender50 else Lavender800)
                }
                TextButton(onClick = { DebugLog.clear() }) {
                    Icon(Icons.Outlined.DeleteSweep, null, modifier = Modifier.size(16.dp), tint = Rose600)
                    Spacer(Modifier.width(4.dp))
                    Text("Bersihkan", color = Rose600, style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                "${entries.size} entri. Urutan terbaru di bawah. Berisi prompt, respons AI, dan operasi DB.",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Lavender400 else Gray600,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada log. Coba proses/simpan sebuah catatan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Lavender400 else Gray400)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(entries) { e ->
                        val accent = when {
                            e.tag.contains("✕") || e.tag.contains("⚠") -> Rose600
                            e.tag.startsWith("AI") -> Sky600
                            e.tag.startsWith("DB") -> Mint600
                            e.tag.startsWith("RAG") -> Lavender600
                            else -> Gray600
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) GlassDark else GlassLight)
                                .padding(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(e.time, style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) Lavender400 else Gray400)
                                Text(e.tag, style = MaterialTheme.typography.labelSmall, color = accent)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                e.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = if (isDark) Lavender50 else Lavender800
                            )
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
                }
            }
        }
    }
}
