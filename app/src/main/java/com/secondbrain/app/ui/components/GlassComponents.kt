package com.secondbrain.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondbrain.app.ui.theme.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemDark()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (isDark) GlassDark else GlassLight)
            .border(
                width = 1.dp,
                color = if (isDark) GlassBorderDark else GlassBorderLight,
                shape = RoundedCornerShape(cornerRadius)
            )
            .padding(12.dp),
        content = content
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
    enabled: Boolean = true
) {
    val isDark = isSystemDark()
    val bg = when {
        accent && isDark  -> Lavender600.copy(alpha = 0.3f)
        accent            -> Lavender100
        isDark            -> GlassDark
        else              -> GlassLight
    }
    val border = when {
        accent && isDark  -> Lavender400.copy(alpha = 0.5f)
        accent            -> Lavender400
        isDark            -> GlassBorderDark
        else              -> GlassBorderLight
    }
    val textColor = when {
        !enabled          -> Gray400
        accent && isDark  -> Lavender200
        accent            -> Lavender600
        isDark            -> Lavender200
        else              -> Lavender600
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Composable
fun GlassModeButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemDark()
    val bg = when {
        selected && isDark  -> Lavender600.copy(alpha = 0.25f)
        selected            -> Lavender50
        isDark              -> GlassDark
        else                -> GlassLight.copy(alpha = 0.5f)
    }
    val borderColor = when {
        selected && isDark  -> Lavender400.copy(alpha = 0.5f)
        selected            -> Lavender400
        isDark              -> GlassBorderDark
        else                -> GlassBorderLight
    }
    val iconColor = if (isDark) {
        if (selected) Lavender200 else Lavender400
    } else {
        if (selected) Lavender600 else Gray400
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = text, tint = iconColor, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) (if (selected) Lavender200 else Lavender400)
                    else (if (selected) Lavender600 else Gray600)
        )
    }
}

@Composable
fun isSystemDark(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()
