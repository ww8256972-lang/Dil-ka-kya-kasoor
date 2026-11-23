package com.waqar.institute.ledger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.GlassCard
import com.example.ui.designsystem.ShimmerButton
import com.example.ui.designsystem.WciColors
import com.example.viewmodel.InstituteUiState
import com.example.viewmodel.InstituteViewModel

@Composable
fun OfflineFeeLedgerScreen(
    uiState: InstituteUiState,
    viewModel: InstituteViewModel
) {
    var showReceiptDialog by remember { mutableStateOf(false) }
    var selectedStudentId by remember { mutableStateOf("") }
    var amountInrText by remember { mutableStateOf("") }
    var selectedPaymentMode by remember { mutableStateOf("MANUAL_CASH_COUNTER") }
    var receiptRemarks by remember { mutableStateOf("") }

    val receipts = OfflineFeeLedgerEngine.localOfflineReceiptsCache.values.toList()
        .sortedByDescending { it.timestamp }
    val ledgers = OfflineFeeLedgerEngine.localStudentFeesLedgerCache.values.toList()

    val isChainValid = remember(receipts) {
        OfflineFeeLedgerEngine.verifyFeeLedgerHashChain(receipts)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card with Single-Campus and Hash Chain verification badge
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Offline Campus Fee Ledger",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = WciColors.textPrimary
                                )
                            )
                            Text(
                                text = "188, DSN College Marg, Ab Nagar, Unnao, UP 209801",
                                style = MaterialTheme.typography.bodySmall.copy(color = WciColors.textMuted)
                            )
                        }

                        // Hash Chain Integrity Pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isChainValid) WciColors.SuccessNeon.copy(alpha = 0.15f) else WciColors.ErrorNeon.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isChainValid) WciColors.SuccessNeon else WciColors.ErrorNeon
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isChainValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isChainValid) WciColors.SuccessNeon else WciColors.ErrorNeon,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isChainValid) "Merkle Chain Verified" else "Chain Tampered",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isChainValid) WciColors.SuccessNeon else WciColors.ErrorNeon,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Strict offline campus-counter receipt accounting. All student fees are flat course amounts minus verified manual receipts with Ed25519 QR signatures (Zero Payment Gateway / Zero Installment).",
                        style = MaterialTheme.typography.bodySmall.copy(color = WciColors.textSecondary)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    ShimmerButton(
                        text = "+ Issue Manual Campus Counter Receipt",
                        icon = Icons.Default.Receipt,
                        onClick = { showReceiptDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Summary Metric Tiles
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val totalCollectedPaise = receipts.sumOf { it.amount_paise }
                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Collected", style = MaterialTheme.typography.labelMedium.copy(color = WciColors.textMuted))
                        Text(
                            text = "₹${totalCollectedPaise / 100L}",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = WciColors.SuccessNeon
                            )
                        )
                        Text("${receipts.size} receipts issued", style = MaterialTheme.typography.labelSmall.copy(color = WciColors.textMuted))
                    }
                }

                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Active Ledgers", style = MaterialTheme.typography.labelMedium.copy(color = WciColors.textMuted))
                        Text(
                            text = "${ledgers.size}",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = WciColors.ElectricCyan
                            )
                        )
                        val clearedCount = ledgers.count { it.payment_status == "CLEARED" }
                        Text("$clearedCount fully cleared", style = MaterialTheme.typography.labelSmall.copy(color = WciColors.textMuted))
                    }
                }
            }
        }

        // Recent Receipts Header
        item {
            Text(
                text = "Recent Campus Counter Receipts (${receipts.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = WciColors.textPrimary
                )
            )
        }

        if (receipts.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(32.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = WciColors.textMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No fee receipts issued yet", style = MaterialTheme.typography.titleSmall.copy(color = WciColors.textSecondary))
                            Text("Issue manual offline receipts using the button above.", style = MaterialTheme.typography.bodySmall.copy(color = WciColors.textMuted))
                        }
                    }
                }
            }
        } else {
            items(receipts, key = { it.id }) { rec ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = rec.receipt_number,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = WciColors.ElectricCyan
                                    )
                                )
                                Text(
                                    text = rec.student_name + " • " + rec.course_name,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = WciColors.textPrimary)
                                )
                            }
                            Text(
                                text = "₹${rec.amount_paise / 100L}",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = WciColors.SuccessNeon
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Mode: ${rec.payment_mode}",
                                style = MaterialTheme.typography.labelSmall.copy(color = WciColors.textMuted)
                            )
                            Text(
                                text = "Hash: ${rec.record_sha256_hash.take(12)}...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = WciColors.textMuted
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // Manual Receipt Entry Modal
    if (showReceiptDialog) {
        AlertDialog(
            onDismissRequest = { showReceiptDialog = false },
            title = {
                Text("Manual Campus Counter Receipt")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Record an official manual fee receipt at campus counter (188, DSN College Marg, Unnao).",
                        style = MaterialTheme.typography.bodySmall.copy(color = WciColors.textSecondary)
                    )

                    OutlinedTextField(
                        value = amountInrText,
                        onValueChange = { amountInrText = it.filter { c -> c.isDigit() } },
                        label = { Text("Amount (INR)") },
                        leadingIcon = { Text("₹") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = receiptRemarks,
                        onValueChange = { receiptRemarks = it },
                        label = { Text("Remarks / Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amountInr = amountInrText.toLongOrNull() ?: 0L
                        if (amountInr > 0L) {
                            val student = uiState.students.firstOrNull { it.id == selectedStudentId }
                                ?: uiState.students.firstOrNull()
                            if (student != null) {
                                viewModel.recordManualOfflineReceipt(
                                    student = student,
                                    amountPaise = amountInr * 100L,
                                    paymentMode = selectedPaymentMode,
                                    remarks = receiptRemarks
                                )
                            }
                        }
                        showReceiptDialog = false
                    }
                ) {
                    Text("Issue Receipt")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReceiptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
