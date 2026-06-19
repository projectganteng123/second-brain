package com.secondbrain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.secondbrain.app.capture.SttSession
import com.secondbrain.app.ui.theme.*

/**
 * Cue rekaman: indikator merekam, label aktif, status serah-terima mic, transkrip parsial live,
 * dan kontrol Selesai/Batal.
 */
@Composable
fun RecordingCue(
    label: String?,
    phase: SttSession.Phase,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val isDark = isSystemDark()
    val listening = phase is SttSession.Phase.Listening
    val partial = (phase as? SttSession.Phase.Listening)?.partial.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Peach600.copy(0.14f) else Peach50)
            .border(1.dp, Peach200, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Peach600))
            Text(
                if (listening) "Mendengarkan…" else "Menyiapkan…",
                style = MaterialTheme.typography.labelMedium,
                color = Peach600
            )
            if (label != null) {
                Text("· $label", style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) Peach200 else Peach800)
            } else {
                Text("· Catatan bebas", style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) Peach200 else Peach800)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            partial.ifBlank { if (listening) "Silakan bicara…" else "" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDark) Lavender50 else Lavender800
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                text = "Selesai",
                icon = Icons.Outlined.Check,
                onClick = onDone,
                accent = true,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                text = "Batal",
                icon = Icons.Outlined.Close,
                onClick = onCancel,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
