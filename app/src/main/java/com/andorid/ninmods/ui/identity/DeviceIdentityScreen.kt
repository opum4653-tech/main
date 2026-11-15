package com.andorid.ninmods.ui.identity

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceIdentityScreen(
    onBack: () -> Unit,
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

    val primaryColor = Color(0xFFE10098)
    val cardBg = Color(0xFF140F20)
    val fieldBg = Color(0xFF1D162E)
    val borderColor = Color(0x33E10098)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ANTI-BLACKLIST & IMEI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Hardware Device Identity & Ban Shield",
                            fontSize = 11.sp,
                            color = Color(0xFFB39DDB)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { refreshInfo() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = primaryColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0C0814)
                )
            )
        },
        containerColor = Color(0xFF0C0814)
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = primaryColor)
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // 1. BAN SHIELD STATUS BANNER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22E10098)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Anti-Blacklist Engine Active",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Real low-level root spoofer: patches SSAID database, system properties & Garena device tokens.",
                                color = Color(0xFF9E8FB2),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // 2. DEVICE MODEL PRESETS (1-TAP GENUINE PROFILES)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "NEW PHONE HARDWARE PRESETS (1-TAP)",
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Select a genuine device profile to randomize all fingerprints according to authentic brand hardware.",
                        color = Color(0xFF9E8FB2),
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeviceIdentityManager.POPULAR_PRESETS.take(3).forEach { p ->
                            val isSelected = selectedPreset == p
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) primaryColor else Color(0xFF1D162E))
                                    .border(
                                        1.dp,
                                        if (isSelected) Color.White else Color(0x33E10098),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        selectedPreset = p
                                        imei1Input = DeviceIdentityManager.generateRealisticImei(p.tacPrefix)
                                        imei2Input = DeviceIdentityManager.generateRealisticImei(p.tacPrefix)
                                        androidIdInput = DeviceIdentityManager.generateAndroidId()
                                        serialInput = DeviceIdentityManager.generateSerialNumber()
                                        macInput = DeviceIdentityManager.generateMacAddress()
                                        Toast.makeText(context, "${p.brand} ${p.model} profile selected!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = p.brand,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = p.model,
                                        color = if (isSelected) Color.White else Color(0xFFB39DDB),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. QUICK ACTION BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            Toast.makeText(context, "Full new identity generated!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF221738)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Casino,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Randomize All", color = Color.White, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            selectedPreset = null
                            imei1Input = state.imei1
                            imei2Input = state.imei2
                            androidIdInput = state.androidId
                            serialInput = state.serialNumber
                            macInput = state.macAddress
                            Toast.makeText(context, "Reverted to current system values", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D162E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = null,
                            tint = Color(0xFFB39DDB),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Fields", color = Color(0xFFB39DDB), fontSize = 13.sp)
                    }
                }

                // 4. EDITABLE HARDWARE FIELDS CARD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "HARDWARE IDENTIFIERS",
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        fontSize = 13.sp
                    )

                    // IMEI 1
                    IdentityInputField(
                        label = "IMEI (Primary Slot)",
                        value = imei1Input,
                        onValueChange = { imei1Input = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(imei1Input)) },
                        onRandomize = { imei1Input = DeviceIdentityManager.generateRealisticImei(selectedPreset?.tacPrefix) }
                    )

                    // IMEI 2
                    IdentityInputField(
                        label = "IMEI 2 (Secondary Slot)",
                        value = imei2Input,
                        onValueChange = { imei2Input = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(imei2Input)) },
                        onRandomize = { imei2Input = DeviceIdentityManager.generateRealisticImei(selectedPreset?.tacPrefix) }
                    )

                    // Android ID (SSAID)
                    IdentityInputField(
                        label = "Android ID (SSAID - 16 Hex)",
                        value = androidIdInput,
                        onValueChange = { androidIdInput = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(androidIdInput)) },
                        onRandomize = { androidIdInput = DeviceIdentityManager.generateAndroidId() }
                    )

                    // Serial Number
                    IdentityInputField(
                        label = "Hardware Serial (ro.serialno)",
                        value = serialInput,
                        onValueChange = { serialInput = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(serialInput)) },
                        onRandomize = { serialInput = DeviceIdentityManager.generateSerialNumber() }
                    )

                    // MAC Address
                    IdentityInputField(
                        label = "Wi-Fi & Bluetooth MAC Address",
                        value = macInput,
                        onValueChange = { macInput = it },
                        onCopy = { clipboardManager.setText(AnnotatedString(macInput)) },
                        onRandomize = { macInput = DeviceIdentityManager.generateMacAddress() }
                    )
                }

                // 5. GAME GUEST & CACHE CLEANUP OPTION
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Wipe Game Blacklist Tokens",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Removes guest login cache & banned hardware hashes for Free Fire Global / MAX.",
                                color = Color(0xFF9E8FB2),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = clearGameGuest,
                            onCheckedChange = { clearGameGuest = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = primaryColor
                            )
                        )
                    }
                }

                // 6. APPLY ACTION BUTTON
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
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    enabled = !isApplying
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "APPLY NEW IDENTITY LIVE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun IdentityInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCopy: () -> Unit,
    onRandomize: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFFB39DDB),
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
                    color = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE10098),
                    unfocusedBorderColor = Color(0x33E10098),
                    focusedContainerColor = Color(0xFF1D162E),
                    unfocusedContainerColor = Color(0xFF1D162E)
                ),
                singleLine = true
            )

            IconButton(
                onClick = onRandomize,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF221738))
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Randomize",
                    tint = Color(0xFFE10098),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF221738))
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = Color(0xFFB39DDB),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
