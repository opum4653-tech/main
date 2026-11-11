package com.andorid.ninmods.ui.dashboard.denylist

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andorid.ninmods.data.DenyListManager
import com.andorid.ninmods.data.RootEnvironmentInfo
import kotlinx.coroutines.launch

@Composable
fun DenyListCard(
    onOpenManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var envInfo by remember { mutableStateOf<RootEnvironmentInfo?>(null) }
    var isCloaking by remember { mutableStateOf(false) }

    fun refreshEnv() {
        scope.launch {
            envInfo = DenyListManager.detectEnvironment()
        }
    }

    LaunchedEffect(Unit) {
        refreshEnv()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF141721),
        border = BorderStroke(1.dp, Color(0xFF232938))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF241C38)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Magisk DenyList",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF3F4F6)
                            )
                        )
                        Text(
                            text = "Root Cloak & Anti-Detection",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF9CA3AF)
                            )
                        )
                    }
                }

                // Status Pill
                val env = envInfo
                val statusText = when {
                    env == null -> "CHECKING"
                    !env.rootAvailable -> "NO ROOT"
                    !env.isMagisk -> "ACTIVE ROOT"
                    env.denylistEnabled -> "ENFORCING"
                    else -> "DISABLED"
                }

                val statusColor = when {
                    env?.denylistEnabled == true -> Color(0xFF10B981)
                    env?.rootAvailable == true -> Color(0xFFF59E0B)
                    else -> Color(0xFFEF4444)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Info row
            envInfo?.let { env ->
                if (env.isMagisk) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1E2B))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Protected Targets",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF)
                            )
                            Text(
                                text = "${env.totalHiddenPackages} apps (${env.totalHiddenProcesses} processes)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF3F4F6)
                            )
                        }

                        Text(
                            text = env.providerName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFA78BFA)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1-Tap Quick Cloak Button
                Button(
                    onClick = {
                        isCloaking = true
                        scope.launch {
                            val res = DenyListManager.quickCloakGames(context)
                            isCloaking = false
                            if (res.isSuccess) {
                                Toast.makeText(context, "Free Fire Protected (${res.getOrNull()} procs)", Toast.LENGTH_SHORT).show()
                                refreshEnv()
                            } else {
                                Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C3AED),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    enabled = !isCloaking
                ) {
                    if (isCloaking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CLOAK GAME",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Open Manager Button
                OutlinedButton(
                    onClick = onOpenManager,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFF3F4F6)
                    ),
                    border = BorderStroke(1.dp, Color(0xFF333B4F)),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = "MANAGE APPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }
        }
    }
}
