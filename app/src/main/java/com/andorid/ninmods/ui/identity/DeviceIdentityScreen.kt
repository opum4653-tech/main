package com.andorid.ninmods.ui.identity

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andorid.ninmods.data.DeviceIdentityManager
import com.andorid.ninmods.data.DeviceIdentityState
import com.andorid.ninmods.data.DeviceProfilePreset
import kotlinx.coroutines.launch

@Composable
fun DeviceIdentityScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf(DeviceIdentityState()) }
    var isLoading by remember { mutableStateOf(true) }
    var isApplying by remember { mutableStateOf(false) }

    var selectedPreset by remember { mutableStateOf<DeviceProfilePreset?>(null) }
    var imei1Input by remember { mutableStateOf("") }
    var imei2Input by remember { mutableStateOf("") }
    var androidIdInput by remember { mutableStateOf("") }
    var serialInput by remember { mutableStateOf("") }
    var macInput by remember { mutableStateOf("") }
    var clearGameGuest by remember { mutableStateOf(true) }

    fun refreshInfo() {
        scope.launch {
            isLoading = true
            val info = DeviceIdentityManager.getDeviceInfo(context)
            state = info
            imei1Input = info.imei1
            imei2Input = info.imei2
            androidIdInput = info.androidId
            serialInput = info.serialNumber
            macInput = info.macAddress
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshInfo()
    }

    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // 1. APATCH-THEMED STATUS BANNER (ElevatedCard 20.dp radius)
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
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Anti-Blacklist Engine",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hardware Device Identity Spoofer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
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
                                        .background(Color(0xFF10B981))
                                )
                                Text(
                                    text = "ROOT READY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Patches SSAID database, system properties via resetprop & cleanses Garena hardware tokens without breaking SELinux.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            // 2. DEVICE MODEL PRESETS (APatch Surface Cards)
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "GENUINE DEVICE PRESETS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Select an authentic hardware profile to generate coordinated IMEI, model & fingerprint data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeviceIdentityManager.POPULAR_PRESETS.take(3).forEach { p ->
                            val isSelected = selectedPreset == p
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedPreset = p
                                        imei1Input = DeviceIdentityManager.generateRealisticImei(p.tacPrefix)
                                        imei2Input = DeviceIdentityManager.generateRealisticImei(p.tacPrefix)
                                        androidIdInput = DeviceIdentityManager.generateAndroidId()
                                        serialInput = DeviceIdentityManager.generateSerialNumber()
                                        macInput = DeviceIdentityManager.generateMacAddress()
                                        Toast.makeText(context, "${p.brand} ${p.model} profile selected", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                shadowElevation = 0.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = p.brand,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = p.model,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. ACTION BUTTONS (APatch Pill Buttons matching Dashboard style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val preset = selectedPreset ?: DeviceIdentityManager.POPULAR_PRESETS.random()
                        selectedPreset = preset
                        imei1Input = DeviceIdentityManager.generateRealisticImei(preset.tacPrefix)
                        imei2Input = DeviceIdentityManager.generateRealisticImei(preset.tacPrefix)
                        androidIdInput = DeviceIdentityManager.generateAndroidId()
                        serialInput = DeviceIdentityManager.generateSerialNumber()
                        macInput = DeviceIdentityManager.generateMacAddress()
                        Toast.makeText(context, "All hardware fields randomized", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Casino,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Randomize All",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = {
                        selectedPreset = null
                        imei1Input = state.imei1
                        imei2Input = state.imei2
                        androidIdInput = state.androidId
                        serialInput = state.serialNumber
                        macInput = state.macAddress
                        Toast.makeText(context, "Reverted to current system values", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Reset Fields",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 4. EDITABLE HARDWARE FIELDS (Matching APatch MaterialTheme surface)
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
                    Text(
                        text = "HARDWARE IDENTIFIERS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // IMEI 1
                    ApatchIdentityField(
                        label = "IMEI (Primary Slot)",
                        value = imei1Input,
                        onValueChange = { imei1Input = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(imei1Input)) },
                        onRandomize = { imei1Input = DeviceIdentityManager.generateRealisticImei(selectedPreset?.tacPrefix) }
                    )

                    // IMEI 2
                    ApatchIdentityField(
                        label = "IMEI 2 (Secondary Slot)",
                        value = imei2Input,
                        onValueChange = { imei2Input = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(imei2Input)) },
                        onRandomize = { imei2Input = DeviceIdentityManager.generateRealisticImei(selectedPreset?.tacPrefix) }
                    )

                    // Android ID (SSAID)
                    ApatchIdentityField(
                        label = "Android ID (SSAID - 16 Hex)",
                        value = androidIdInput,
                        onValueChange = { androidIdInput = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(androidIdInput)) },
                        onRandomize = { androidIdInput = DeviceIdentityManager.generateAndroidId() }
                    )

                    // Serial Number
                    ApatchIdentityField(
                        label = "Hardware Serial (ro.serialno)",
                        value = serialInput,
                        onValueChange = { serialInput = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(serialInput)) },
                        onRandomize = { serialInput = DeviceIdentityManager.generateSerialNumber() }
                    )

                    // MAC Address
                    ApatchIdentityField(
                        label = "Wi-Fi & Bluetooth MAC Address",
                        value = macInput,
                        onValueChange = { macInput = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(macInput)) },
                        onRandomize = { macInput = DeviceIdentityManager.generateMacAddress() }
                    )
                }
            }

            // 5. GAME GUEST & CACHE CLEANUP OPTION
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wipe Game Blacklist Tokens",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Cleans guest login hashes & banned hardware device tokens for Free Fire Global / MAX.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = clearGameGuest,
                        onCheckedChange = { clearGameGuest = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            // 6. APPLY ACTION BUTTON (APatch 16.dp Rounded Button)
            Button(
                onClick = {
                    scope.launch {
                        isApplying = true
                        val res = DeviceIdentityManager.applyNewIdentity(
                            newAndroidId = androidIdInput,
                            newSerial = serialInput,
                            newImei1 = imei1Input,
                            newImei2 = imei2Input,
                            newMac = macInput,
                            preset = selectedPreset,
                            resetGameGuest = clearGameGuest
                        )
                        isApplying = false
                        if (res.isSuccess) {
                            Toast.makeText(context, "Anti-Blacklist applied! Device ID updated.", Toast.LENGTH_LONG).show()
                            refreshInfo()
                        } else {
                            Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                enabled = !isApplying
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "APPLY NEW IDENTITY LIVE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ApatchIdentityField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCopy: () -> Unit,
    onRandomize: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true
            )

            IconButton(
                onClick = onRandomize,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Randomize",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
