package com.secondbrain.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.components.*
import com.secondbrain.app.ui.theme.*
import com.secondbrain.app.util.PrefsManager

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDebug: () -> Unit = {}) {
    val isDark = isSystemDark()
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }

    var apiKey by remember { mutableStateOf(prefs.getApiKey()) }
    var showKey by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, "Kembali", tint = if (isDark) Lavender200 else Lavender600)
                }
                Text(
                    "Pengaturan",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Lavender50 else Lavender800
                )
            }

            Spacer(Modifier.height(16.dp))

            GlassCard {
                SectionLabel("Gemini API", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Dapatkan API key gratis di Google AI Studio (aistudio.google.com).",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None
                                          else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                null,
                                tint = if (isDark) Lavender400 else Gray400
                            )
                        }
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Lavender400,
                        unfocusedBorderColor = if (isDark) GlassBorderDark else Lavender200,
                        focusedContainerColor = if (isDark) GlassDark else GlassLight,
                        unfocusedContainerColor = if (isDark) GlassDark else GlassLight,
                        focusedTextColor = if (isDark) Lavender50 else Lavender800,
                        unfocusedTextColor = if (isDark) Lavender50 else Lavender800,
                        focusedLabelColor = Lavender600,
                        unfocusedLabelColor = if (isDark) Lavender400 else Gray400
                    )
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = if (saved) "Tersimpan" else "Simpan API Key",
                    icon = if (saved) Icons.Outlined.CheckCircle else Icons.Outlined.Save,
                    onClick = {
                        prefs.saveApiKey(apiKey.trim())
                        saved = true
                    },
                    accent = !saved,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(12.dp))

            GlassCard {
                SectionLabel("pengembang", modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    "Lihat apa yang dikirim ke AI, respons baliknya, dan operasi simpan/cari di database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Lavender400 else Gray600
                )
                Spacer(Modifier.height(10.dp))
                GlassButton(
                    text = "Buka Debug Log",
                    icon = Icons.Outlined.BugReport,
                    onClick = onOpenDebug,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
