package com.andorid.ninmods.ui.licenses

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.UserLicenseItem
import com.andorid.ninmods.ui.clock.IndividualLicenseClockCard
import com.andorid.ninmods.ui.clock.LicenseClockCard
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * APatch Dedicated My Licenses Screen.
 * Displays real-time server license expiration clock, active plan details,
 * and the complete inventory of purchased keys with HWID binding & reset controls.
 */
@Composable
fun MyLicensesScreen(
    sessionManager: SessionManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = sessionManager.authToken ?: ""

    var userKeys by remember { mutableStateOf<List<UserLicenseItem>>(emptyList()) }
    var isLoadingKeys by remember { mutableStateOf(false) }
    var resettingKeyId by remember { mutableStateOf<Int?>(null) }

    fun loadKeys() {
        if (token.isBlank()) return
        scope.launch {
            isLoadingKeys = true
            AuthApiClient.getMyLicenseKeys(token).onSuccess { list ->
                userKeys = list
            }
            isLoadingKeys = false
        }
    }

    LaunchedEffect(Unit) {
        loadKeys()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Digital License Expiration Clock (Monotonic Counter)
        item {
            LicenseClockCard(sessionManager = sessionManager)
        }

        // 2. Server Active License Key Card (If active key exists)
        if (!sessionManager.activeLicenseKey.isNullOrBlank()) {
            val keyString = sessionManager.activeLicenseKey ?: ""
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Current Active License",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = keyString,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("License Key", keyString)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "License key copied!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy License Key",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Inventory Header with Refresh
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "MY LICENSE KEYS (${userKeys.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                IconButton(onClick = { loadKeys() }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh Keys",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 4. Purchased License Keys List
        if (isLoadingKeys && userKeys.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                }
            }
        } else if (userKeys.isEmpty()) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Purchased Keys Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use Add Funds in menu to deposit balance and purchase VIP subscriptions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(userKeys, key = { it.id }) { key ->
                val isExpired = key.status.equals("expired", ignoreCase = true)
                val isActive = key.status.equals("active", ignoreCase = true)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Dedicated Live Countdown Clock Timer Card for each active license
                    if (isActive || key.remainingSeconds > 0L) {
                        IndividualLicenseClockCard(
                            initialRemainingSeconds = key.remainingSeconds,
                            title = "LICENSE REMAINING (${key.durationType.uppercase(Locale.US)})",
                            statusText = if (isActive) "ACTIVE" else "EXPIRED",
                            isActive = isActive
                        )
                    }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = key.licenseKey,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("License Key", key.licenseKey)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "License key copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy Key",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${key.gameType.uppercase(Locale.US)} • ${key.durationType.uppercase(Locale.US)} (${key.durationHours}h)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isActive -> Color(0xFF81C784).copy(alpha = 0.2f)
                                        isExpired -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    }
                                ) {
                                    Text(
                                        text = key.status.uppercase(Locale.US),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isActive -> Color(0xFF81C784)
                                            isExpired -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            }
                        }

                        if (!key.hwid.isNullOrBlank()) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Bound Device: ${key.deviceModel ?: "Active Phone"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "HWID: ${key.hwid}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val isResetting = resettingKeyId == key.id
                                OutlinedButton(
                                    onClick = {
                                        resettingKeyId = key.id
                                        scope.launch {
                                            val res = AuthApiClient.resetMyKeyHwid(token, key.id)
                                            resettingKeyId = null
                                            res.onSuccess { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                loadKeys()
                                            }.onFailure { err ->
                                                Toast.makeText(context, err.message ?: "Reset failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = !isResetting,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    if (isResetting) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                    } else {
                                        Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reset HWID", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
