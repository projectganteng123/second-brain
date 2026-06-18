package com.secondbrain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.secondbrain.app.data.model.*
import com.secondbrain.app.ui.theme.*

@Composable
fun NoteCard(
    title: String,
    type: NoteType,
    timeRange: String?,
    prioritas: Priority?,
    status: NoteStatus?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDone = status == NoteStatus.SELESAI
    val isDark = isSystemDark()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) GlassDark else GlassLight)
            .border(
                1.dp,
                if (isDark) GlassBorderDark else GlassBorderLight,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isDark) Lavender50 else Lavender800,
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TypeChip(type)
                if (!timeRange.isNullOrBlank()) {
                    Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(11.dp), tint = Gray400)
                    Text(timeRange, style = MaterialTheme.typography.labelSmall, color = Gray400)
                }
            }
        }
        if (prioritas != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(priorityColor(prioritas))
            )
        }
    }
}

@Composable
fun TypeChip(type: NoteType) {
    val (bg, fg) = typeColors(type)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(type.label, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
fun typeColors(type: NoteType): Pair<Color, Color> = when (type) {
    NoteType.MEETING  -> Pair(Sky50,   Sky600)
    NoteType.TASK     -> Pair(Mint50,  Mint600)
    NoteType.REMINDER -> Pair(Lemon50, Lemon600)
    NoteType.EVENT    -> Pair(Peach50, Peach600)
    NoteType.IDEA     -> Pair(Rose50,  Rose600)
    NoteType.PERSONAL -> Pair(Lavender50, Lavender600)
    NoteType.NOTE     -> Pair(Gray100, Gray600)
}

fun priorityColor(p: Priority): Color = when (p) {
    Priority.PENTING_URGEN          -> DotUrgentImportant
    Priority.PENTING_TIDAK_URGEN    -> DotImportantOnly
    Priority.URGEN_TIDAK_PENTING    -> DotUrgentOnly
    Priority.TIDAK_PENTING_TIDAK_URGEN -> DotNeutral
}
