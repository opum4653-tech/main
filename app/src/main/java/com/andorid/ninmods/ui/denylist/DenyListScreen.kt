package com.andorid.ninmods.ui.denylist

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.andorid.ninmods.data.AppProcessItem
import com.andorid.ninmods.data.DenyListManager
import com.andorid.ninmods.data.InstalledAppItem
import com.andorid.ninmods.data.RootEnvironmentInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DenyListFilter(val label: String) {
    ALL("All"),
    PROTECTED("Protected"),
    TARGETS("Games & Targets")
}

@Composable
fun DenyListScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var envInfo by remember { mutableStateOf<RootEnvironmentInfo?>(null) }
    var appsList by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isQuickCloaking by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(DenyListFilter.TARGETS) }
    var expandedPackages by remember { mutableStateOf(setOf<String>()) }

    fun refreshData() {
        isLoading = true
        scope.launch {
            val env = DenyListManager.detectEnvironment()
            envInfo = env
            val apps = if (env.rootAvailable) {
                DenyListManager.getInstalledApps(context)
            } else {
                emptyList()
            }
            appsList = apps
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    val filteredApps = remember(appsList, searchQuery, activeFilter) {
        appsList.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (activeFilter) {
                DenyListFilter.ALL -> true
                DenyListFilter.PROTECTED -> app.isFullyHidden || app.isPartiallyHidden
                DenyListFilter.TARGETS -> app.isGameOrTarget
            }

            matchesQuery && matchesFilter
        }
    }

    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFA78BFA))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Inspecting Zygisk & Scanning Packages...",
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Refresh & Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Magisk DenyList",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF3F4F6)
                        )
                        Text(
                            text = "Zygote mount namespace isolation & root cloak",
                            fontSize = 11.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }

                    IconButton(
                        onClick = { refreshData() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFFA78BFA)
                        )
                    }
                }
            }

            item {
                // Environment & Global Toggle Card
                envInfo?.let { env ->
                    DenyListEnvironmentCard(
                        env = env,
                        onToggleDenyList = { enable ->
                            scope.launch {
                                val success = DenyListManager.setDenyListEnabled(enable)
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        if (enable) "DenyList Enforced" else "DenyList Disabled",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    refreshData()
                                } else {
                                    Toast.makeText(context, "Failed to toggle DenyList", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            // 1-Tap Quick Action Card for Free Fire & Play Services
            item {
                QuickGameCloakCard(
                    isQuickCloaking = isQuickCloaking,
                    onQuickCloak = {
                        isQuickCloaking = true
                        scope.launch {
                            val result = DenyListManager.quickCloakGames(context)
                            isQuickCloaking = false
                            if (result.isSuccess) {
                                val count = result.getOrDefault(0)
                                Toast.makeText(
                                    context,
                                    "Protected Free Fire & Play Services ($count processes)",
                                    Toast.LENGTH_LONG
                                ).show()
                                refreshData()
                            } else {
                                val err = result.exceptionOrNull()?.message ?: "Operation failed"
                                Toast.makeText(context, "Error: $err", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            // Search & Filter Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search app or package name...", color = Color(0xFF6B7280), fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF9CA3AF)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF161922),
                            unfocusedContainerColor = Color(0xFF161922),
                            focusedBorderColor = Color(0xFFA78BFA),
                            unfocusedBorderColor = Color(0xFF262C3A),
                            focusedTextColor = Color(0xFFF3F4F6),
                            unfocusedTextColor = Color(0xFFF3F4F6)
                        )
                    )

                    // Filter Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DenyListFilter.values().forEach { filter ->
                            val isSelected = activeFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { activeFilter = filter },
                                label = {
                                    Text(
                                        text = filter.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = Color(0xFF1A1D27),
                                    labelColor = Color(0xFF9CA3AF),
                                    selectedContainerColor = Color(0xFF2D1F47),
                                    selectedLabelColor = Color(0xFFA78BFA)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color(0xFF2E3445),
                                    selectedBorderColor = Color(0xFFA78BFA)
                                )
                            )
                        }
                    }
                }
            }

            // Apps List Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INSTALLED APPLICATIONS (${filteredApps.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6B7280),
                        letterSpacing = 1.sp
                    )
                }
            }

            if (filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching applications found",
                            color = Color(0xFF6B7280),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { appItem ->
                    val isExpanded = expandedPackages.contains(appItem.packageName)
                    AppItemCard(
                        app = appItem,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedPackages = if (isExpanded) {
                                expandedPackages - appItem.packageName
                            } else {
                                expandedPackages + appItem.packageName
                            }
                        },
                        onToggleAllProcesses = { enable ->
                            scope.launch {
                                val success = if (enable) {
                                    DenyListManager.addToDenyList(appItem.packageName)
                                } else {
                                    DenyListManager.removeFromDenyList(appItem.packageName)
                                }
                                if (success) {
                                    refreshData()
                                } else {
                                    Toast.makeText(context, "Operation failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onToggleSingleProcess = { procName, enable ->
                            scope.launch {
                                val success = if (enable) {
                                    DenyListManager.addToDenyList(appItem.packageName, procName)
                                } else {
                                    DenyListManager.removeFromDenyList(appItem.packageName, procName)
                                }
                                if (success) {
                                    refreshData()
                                } else {
                                    Toast.makeText(context, "Operation failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
}

@Composable
fun DenyListEnvironmentCard(
    env: RootEnvironmentInfo,
    onToggleDenyList: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161922),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C3A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (env.rootAvailable) Color(0xFF10B981) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = env.providerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFF3F4F6)
                    )
                }

                if (env.isMagisk) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (env.denylistEnabled) Color(0xFF064E3B) else Color(0xFF451A03)
                    ) {
                        Text(
                            text = if (env.denylistEnabled) "ENFORCING" else "DISABLED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (env.denylistEnabled) Color(0xFF34D399) else Color(0xFFFBBF24),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = env.statusMessage,
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
            )

            if (env.isMagisk) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color(0xFF222836))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enforce DenyList",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF3F4F6)
                        )
                        Text(
                            text = "Unmount root files & modules in selected targets",
                            fontSize = 11.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                    Switch(
                        checked = env.denylistEnabled,
                        onCheckedChange = onToggleDenyList,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFFFFF),
                            checkedTrackColor = Color(0xFF8B5CF6),
                            uncheckedThumbColor = Color(0xFF9CA3AF),
                            uncheckedTrackColor = Color(0xFF262C3A)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Packages: ${env.totalHiddenPackages}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFA78BFA)
                    )
                    Text(
                        text = "Processes: ${env.totalHiddenProcesses}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF34D399)
                    )
                }
            }
        }
    }
}

@Composable
fun QuickGameCloakCard(
    isQuickCloaking: Boolean,
    onQuickCloak: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E172F),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4C2889))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = Color(0xFFA78BFA),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "1-Tap Auto-Cloak Games",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF3F4F6)
                    )
                    Text(
                        text = "Free Fire, Free Fire MAX, Google Play Services",
                        fontSize = 11.sp,
                        color = Color(0xFFC4B5FD)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onQuickCloak,
                enabled = !isQuickCloaking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C3AED),
                    contentColor = Color.White
                )
            ) {
                if (isQuickCloaking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cloaking...", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CLOAK ALL TARGETS NOW", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AppItemCard(
    app: InstalledAppItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleAllProcesses: (Boolean) -> Unit,
    onToggleSingleProcess: (String, Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161922),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (app.isFullyHidden) Color(0xFF059669)
            else if (app.isPartiallyHidden) Color(0xFFD97706)
            else Color(0xFF262C3A)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = app.packageName,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFFF3F4F6),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (app.isGameOrTarget) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF3B1D54)
                            ) {
                                Text(
                                    text = "TARGET",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA78BFA),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when {
                                app.isFullyHidden -> Color(0xFF064E3B)
                                app.isPartiallyHidden -> Color(0xFF451A03)
                                else -> Color(0xFF1F2430)
                            }
                        ) {
                            Text(
                                text = when {
                                    app.isFullyHidden -> "PROTECTED (${app.processes.size}/${app.processes.size})"
                                    app.isPartiallyHidden -> "PARTIAL (${app.processes.count { it.isHidden }}/${app.processes.size})"
                                    else -> "EXPOSED"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    app.isFullyHidden -> Color(0xFF34D399)
                                    app.isPartiallyHidden -> Color(0xFFFBBF24)
                                    else -> Color(0xFF6B7280)
                                },
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        if (app.processes.size > 1) {
                            Text(
                                text = "${app.processes.size} processes",
                                fontSize = 10.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = app.isFullyHidden,
                    onCheckedChange = onToggleAllProcesses,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFFFFFF),
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedThumbColor = Color(0xFF6B7280),
                        uncheckedTrackColor = Color(0xFF262C3A)
                    )
                )

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = "Expand",
                        tint = Color(0xFF9CA3AF)
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Divider(color = Color(0xFF262C3A))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "COMPONENT PROCESSES:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9CA3AF),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    app.processes.forEach { proc ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = proc.processName,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (proc.isHidden) Color(0xFF34D399) else Color(0xFF9CA3AF),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Checkbox(
                                checked = proc.isHidden,
                                onCheckedChange = { checked ->
                                    onToggleSingleProcess(proc.processName, checked)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF10B981),
                                    uncheckedColor = Color(0xFF4B5563),
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIcon(
    packageName: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var drawable by remember(packageName) { mutableStateOf<Drawable?>(null) }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) {
            try {
                drawable = context.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {}
        }
    }

    if (drawable != null) {
        AsyncImage(
            model = drawable,
            contentDescription = null,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Color(0xFF262C3A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = null,
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
