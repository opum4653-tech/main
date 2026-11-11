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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun AddFundsScreen(
    sessionManager: SessionManager,
    onNavigateBack: () -> Unit,
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
                // Dropdown Field for Payment Method
                Text(
                    text = "Payment Method",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isSubmitting && paymentMethods.isNotEmpty()) {
                            showGatewayPicker = true
                        },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isCrypto = selectedMethod?.methodCode?.contains("binance") == true
                            Icon(
                                imageVector = if (isCrypto) Icons.Filled.CurrencyBitcoin else Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = selectedMethod?.name ?: "Select Payment Method",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                selectedMethod?.let { m ->
                                    Text(
                                        text = "Limits: $${m.minDeposit} - $${m.maxDeposit} ${m.currency}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Open selector",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Selected Method Details Card & Copy
                selectedMethod?.let { method ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Payment Account Details",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = method.accountDetails,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(onClick = {
                                    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clip.setPrimaryClip(ClipData.newPlainText("Payment Info", method.accountDetails))
                                    Toast.makeText(context, "Account details copied!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (method.instructions.isNotBlank()) {
                                Text(
                                    text = method.instructions,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Deposit Amount Input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Amount to Deposit ($)") },
                    placeholder = { Text("e.g. 10.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                // Transaction ID Input
                OutlinedTextField(
                    value = trxIdText,
                    onValueChange = { trxIdText = it },
                    label = { Text("Transaction ID (TrxID / Hash)") },
                    placeholder = { Text("e.g. 9J4728519B or Order ID") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                // Sender Note / Phone
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Sender Note / Phone Number (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                )

                val parsedAmt = amountText.toDoubleOrNull() ?: 0.0
                val canSubmit = selectedMethod != null &&
                        parsedAmt > 0 &&
                        trxIdText.trim().length >= 4 &&
                        !isSubmitting

                Button(
                    onClick = {
                        val m = selectedMethod ?: return@Button
                        if (!canSubmit) return@Button
                        scope.launch {
                            isSubmitting = true
                            val res = AuthApiClient.submitDeposit(
                                apiKey = token,
                                methodCode = m.methodCode,
                                amount = parsedAmt,
                                trxId = trxIdText.trim(),
                                notes = notesText.trim()
                            )
                            withContext(Dispatchers.Main) {
                                isSubmitting = false
                                res.onSuccess { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    // Refresh balance
                                    scope.launch {
                                        AuthApiClient.getProfileWithEtag(token).onSuccess { (profileData, _, _) ->
                                            if (profileData != null) {
                                                sessionManager.userBalance = profileData.balance
                                            }
                                        }
                                    }
                                    onNavigateBack()
                                }.onFailure { err ->
                                    Toast.makeText(context, err.message ?: "Failed to submit deposit", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Submitting to Server...")
                    } else {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SUBMIT DEPOSIT REQUEST", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

    // Driver-loader styled selection bottom sheet for payment methods
    if (showGatewayPicker) {
        val sheetItems = paymentMethods.map { method ->
            SheetItem(
                value = method,
                title = method.name,
                subtitle = "Limits: $${method.minDeposit} - $${method.maxDeposit} ${method.currency}",
                icon = if (method.methodCode.contains("binance")) Icons.Filled.CurrencyBitcoin else Icons.Filled.AccountBalanceWallet
            )
        }

        AppSelectionBottomSheet(
            title = "Select Payment Gateway",
            subtitle = "Choose your preferred payment method",
            items = sheetItems,
            initialSelected = selectedMethod,
            actionButtonText = "Select Gateway",
            onDismissRequest = { showGatewayPicker = false },
            onItemSelected = { selected ->
                selectedMethod = selected
                showGatewayPicker = false
            }
        )
    }
}
