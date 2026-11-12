package com.andorid.ninmods.ui.deposit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.PaymentMethodItem
import com.andorid.ninmods.ui.dialogs.AppSelectionBottomSheet
import com.andorid.ninmods.ui.dialogs.SheetItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APatch Unified Deposit Submission Screen.
 * Pure container composable with zero nested top app bars.
 */
@Composable
fun SubmitDepositScreen(
    sessionManager: SessionManager,
    onDepositSubmitted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = sessionManager.authToken ?: ""

    var paymentMethods by remember { mutableStateOf<List<PaymentMethodItem>>(emptyList()) }
    var selectedMethod by remember { mutableStateOf<PaymentMethodItem?>(null) }
    var isLoadingMethods by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showGatewayPicker by remember { mutableStateOf(false) }

    var amountText by remember { mutableStateOf("") }
    var trxIdText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingMethods = true
        errorMessage = null
        val res = AuthApiClient.getPaymentMethods(token)
        withContext(Dispatchers.Main) {
            isLoadingMethods = false
            res.onSuccess { list ->
                paymentMethods = list
                selectedMethod = list.firstOrNull()
            }.onFailure { err ->
                errorMessage = err.message ?: "Failed to load payment options"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Current Balance Overview Card
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Current Wallet Balance",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val balanceStr = String.format(java.util.Locale.US, "%.2f", sessionManager.userBalance)
                    Text(
                        text = "$$balanceStr USD",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(24.dp)
                    )
                }
            }
        }

        if (isLoadingMethods) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (errorMessage != null && paymentMethods.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage ?: "Failed to connect to payment server",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            // Gateway Selector Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showGatewayPicker = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = selectedMethod?.name ?: "Select Payment Method",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                selectedMethod?.let {
                                    Text(
                                        text = "Limits: $${it.minDeposit} - $${it.maxDeposit} USD",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Payment Instructions Card
            selectedMethod?.let { method ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Payment Account Details",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (!method.accountDetails.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = method.accountDetails,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Account", method.accountDetails))
                                        Toast.makeText(context, "Account details copied", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (!method.instructions.isNullOrBlank()) {
                            Text(
                                text = method.instructions,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Amount to Deposit ($)") },
                placeholder = { Text("e.g. 10.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // TrxID Input
            OutlinedTextField(
                value = trxIdText,
                onValueChange = { trxIdText = it },
                label = { Text("Transaction ID (TrxID / Hash)") },
                placeholder = { Text("e.g. 9B87F293A1") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Sender Note / Details Input
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Sender Note / Phone Number (Optional)") },
                placeholder = { Text("e.g. Sent from 017xxxxxxxx") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Submit Button
            Button(
                onClick = {
                    val method = selectedMethod
                    if (method == null) {
                        Toast.makeText(context, "Please select a payment gateway", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val amountVal = amountText.toDoubleOrNull()
                    if (amountVal == null || amountVal <= 0.0) {
                        Toast.makeText(context, "Please enter a valid deposit amount", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (amountVal < method.minDeposit || amountVal > method.maxDeposit) {
                        Toast.makeText(
                            context,
                            "Amount must be between $${method.minDeposit} and $${method.maxDeposit}",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }
                    if (trxIdText.trim().isBlank()) {
                        Toast.makeText(context, "Please enter the Transaction ID (TrxID)", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
                    scope.launch {
                        val res = AuthApiClient.submitDeposit(
                            apiKey = token,
                            methodCode = method.methodCode,
                            amount = amountVal,
                            trxId = trxIdText.trim(),
                            notes = notesText.trim()
                        )
                        withContext(Dispatchers.Main) {
                            isSubmitting = false
                            res.onSuccess {
                                Toast.makeText(context, "Deposit request submitted successfully!", Toast.LENGTH_LONG).show()
                                onDepositSubmitted()
                            }.onFailure { err ->
                                Toast.makeText(context, err.message ?: "Deposit submission failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Submitting Deposit...")
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SUBMIT DEPOSIT REQUEST", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Modal Bottom Sheet Gateway Picker
    if (showGatewayPicker && paymentMethods.isNotEmpty()) {
        val sheetItems = paymentMethods.map { m ->
            SheetItem(
                value = m,
                title = m.name,
                subtitle = "Limits: $${m.minDeposit} - $${m.maxDeposit} USD"
            )
        }

        AppSelectionBottomSheet(
            title = "Choose Payment Gateway",
            items = sheetItems,
            initialSelected = selectedMethod,
            onDismissRequest = { showGatewayPicker = false },
            onItemSelected = { selected ->
                selectedMethod = selected
                showGatewayPicker = false
            }
        )
    }
}
