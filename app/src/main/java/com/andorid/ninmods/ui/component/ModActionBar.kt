package com.andorid.ninmods.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Universal Official 4-Action Serial Icon Buttons for Dashboard Mods.
 * Strict State Machine:
 * - When NOT downloaded: ONLY Download is enabled. Stop, Start, Update are disabled & dimmed.
 * - When DOWNLOADED & NOT running: Start & Update are enabled. Download is disabled. Stop is disabled.
 * - When RUNNING: ONLY Stop is enabled. Start, Update, Download are disabled.
 *
 * Sequence (from right-to-left as specified by dj):
 * [STOP] [START] [UPDATE] [DOWNLOAD (right-most)]
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
        // 1. STOP Button
        IconButton(
            onClick = onStopClick,
            enabled = stopEnabled,
            modifier = Modifier.size(38.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (stopEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else Color.Transparent,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "Stop Mod",
                        tint = if (stopEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 2. START Button
        IconButton(
            onClick = onStartClick,
            enabled = startEnabled,
            modifier = Modifier.size(38.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (startEnabled) accentColor.copy(alpha = 0.15f) else Color.Transparent,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Start Mod",
                        tint = if (startEnabled) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 3. UPDATE Button
        IconButton(
            onClick = onUpdateClick,
            enabled = updateEnabled,
            modifier = Modifier.size(38.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (updateEnabled) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else Color.Transparent,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdateAlt,
                        contentDescription = "Update Mod",
                        tint = if (updateEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 4. DOWNLOAD Button (Right-most)
        IconButton(
            onClick = onDownloadClick,
            enabled = downloadEnabled,
            modifier = Modifier.size(38.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (downloadEnabled) accentColor.copy(alpha = 0.15f) else Color.Transparent,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = "Download Mod",
                        tint = if (downloadEnabled) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
