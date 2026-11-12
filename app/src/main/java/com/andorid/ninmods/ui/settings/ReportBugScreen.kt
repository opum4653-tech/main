package com.andorid.ninmods.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andorid.ninmods.config.AppConfig
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.util.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APatch Unified Bug Report Screen.
 * Pure container composable adhering 100% to APatch ElevatedCard hierarchy.
 */
@Composable
fun ReportBugScreen(
    sessionManager: SessionManager,
    onReportSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var includeDiagnosticLogs by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }

    val deviceModel = remember { "${Build.MANUFACTURER} ${Build.MODEL}" }
    val androidVersion = remember { "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
    val kernelVersion = remember { System.getProperty("os.version") ?: "Linux" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // 1. Diagnostic Metrics Card
        Text(
            text = "SYSTEM ENVIRONMENT",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Auto-Detected Metrics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Text(
                    text = "Device: $deviceModel • $androidVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Kernel: $kernelVersion • Build: ${AppConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Bug Details Input Card
        Text(
            text = "BUG REPORT DETAILS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Issue Title") },
                    placeholder = { Text("e.g. Kernel driver fails to bind after reboot") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Problem Description & Steps to Reproduce") },
                    placeholder = { Text("Explain what happened, expected behavior, and error messages...") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    enabled = !isSubmitting
                )

                // Include Diagnostic Logs Switch Row inside Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Attach Diagnostic Log",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Captures kernel dmesg / logcat buffer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = includeDiagnosticLogs,
                            onCheckedChange = { includeDiagnosticLogs = it },
                            enabled = !isSubmitting
                        )
                    }
                }

                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Please enter an issue title", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (description.isBlank()) {
                            Toast.makeText(context, "Please describe the problem", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isSubmitting = true
                        scope.launch {
                            val hwid = withContext(Dispatchers.IO) { DeviceIdentifier.getHwid(context) }
                            val isRooted = withContext(Dispatchers.IO) { ShellExecutor.isRootAvailable() }
                            val logs = if (includeDiagnosticLogs) {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val proc = Runtime.getRuntime().exec("logcat -d -t 200")
                                        proc.inputStream.bufferedReader().readText()
                                    }.getOrNull() ?: "Logcat capture unavailable"
                                }
                            } else null

                            val payload = buildString {
                                append("Title: ").appendLine(title)
                                append("Description: ").appendLine(description)
                                append("HWID: ").appendLine(hwid)
                                append("Device: ").appendLine(deviceModel)
                                append("Android: ").appendLine(androidVersion)
                                append("Kernel: ").appendLine(kernelVersion)
                                append("Root Status: ").appendLine(if (isRooted) "Rooted / Permissive" else "Non-Root")
                                if (!logs.isNullOrBlank()) {
                                    appendLine("\n--- LOGCAT DUMP ---")
                                    appendLine(logs)
                                }
                            }

                            val token = sessionManager.authToken ?: ""
                            val res = AuthApiClient.submitBugReport(token, title, payload)
                            withContext(Dispatchers.Main) {
                                isSubmitting = false
                                res.onSuccess {
                                    Toast.makeText(context, "Bug report submitted successfully! Thank you.", Toast.LENGTH_LONG).show()
                                    onReportSubmitted()
                                }.onFailure { err ->
                                    Toast.makeText(context, err.message ?: "Submission failed", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting && title.isNotBlank() && description.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Submitting Diagnostic...")
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SUBMIT BUG REPORT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
