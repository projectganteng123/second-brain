package com.secondbrain.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.theme.*

@Composable
fun MetadataRow(label: String, value: String, modifier: Modifier = Modifier) {
    val isDark = isSystemDark()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Lavender400 else Gray600,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (isDark) Lavender50 else Lavender800,
            modifier = Modifier.weight(0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
    HorizontalDivider(color = if (isDark) GlassBorderDark else GlassBorderLight, thickness = 0.5.dp)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val isDark = isSystemDark()
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (isDark) Lavender400 else Gray400,
        letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp),
        modifier = modifier.padding(bottom = 6.dp)
    )
}
