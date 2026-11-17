package com.andorid.ninmods.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Universal Official 4-Action Serial Icon Buttons for Dashboard Mods.
 * Rendered as structured box-style action tiles:
 * [STOP] [START] [UPDATE] [DOWNLOAD (right-most)]
 *
 * Strict State Machine:
 * - When NOT downloaded: ONLY Download is enabled. Stop, Start, Update are disabled & dimmed boxes.
 * - When DOWNLOADED & NOT running: Start & Update are enabled. Download is disabled. Stop is disabled.
 * - When RUNNING: ONLY Stop is enabled (danger alert box). Start, Update, Download are disabled.
 */
@Composable
fun ModActionButtonsRow(
    isDownloaded: Boolean,
    isRunning: Boolean,
    onStopClick: () -> Unit,
    onStartClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val stopEnabled = isRunning
    val startEnabled = isDownloaded && !isRunning
    val updateEnabled = isDownloaded && !isRunning
    val downloadEnabled = !isDownloaded

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. STOP Box Button
        ModActionBoxButton(
            icon = Icons.Filled.Stop,
            contentDescription = "Stop Mod",
            enabled = stopEnabled,
            onClick = onStopClick,
            activeContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            activeBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
            activeIconColor = MaterialTheme.colorScheme.error,
            iconSize = 20.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 2. START Box Button
        ModActionBoxButton(
            icon = Icons.Filled.PlayArrow,
            contentDescription = "Start Mod",
            enabled = startEnabled,
            onClick = onStartClick,
            activeContainerColor = Color(0xFF22C55E).copy(alpha = 0.18f),
            activeBorderColor = Color(0xFF22C55E).copy(alpha = 0.5f),
            activeIconColor = Color(0xFF22C55E),
            iconSize = 22.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 3. UPDATE Box Button
        ModActionBoxButton(
            icon = Icons.Filled.SystemUpdateAlt,
            contentDescription = "Update Mod",
            enabled = updateEnabled,
            onClick = onUpdateClick,
            activeContainerColor = accentColor.copy(alpha = 0.18f),
            activeBorderColor = accentColor.copy(alpha = 0.45f),
            activeIconColor = accentColor,
            iconSize = 19.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 4. DOWNLOAD Box Button (Right-most)
        ModActionBoxButton(
            icon = Icons.Filled.CloudDownload,
            contentDescription = "Download Mod",
            enabled = downloadEnabled,
            onClick = onDownloadClick,
            activeContainerColor = accentColor.copy(alpha = 0.22f),
            activeBorderColor = accentColor.copy(alpha = 0.55f),
            activeIconColor = accentColor,
            iconSize = 20.dp
        )
    }
}

@Composable
private fun ModActionBoxButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    activeContainerColor: Color,
    activeBorderColor: Color,
    activeIconColor: Color,
    iconSize: Dp = 20.dp
) {
    val containerColor = if (enabled) activeContainerColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val borderColor = if (enabled) activeBorderColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    val iconColor = if (enabled) activeIconColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.size(38.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
