package com.waqar.institute.ui.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wci.ui.liquidglass.components.GlassButton
import com.wci.ui.liquidglass.components.GlassButtonVariant
import com.wci.ui.liquidglass.components.GlassCard
import com.wci.ui.liquidglass.components.GlassPill
import com.wci.ui.liquidglass.theme.GlassShaderTokens
import com.waqar.institute.ui.student.StudentReceiptItem

/**
 * ParentFeeLedgerScreen (M4.4: F-476 to F-490)
 * 100% Offline Fee Receipt Verification & Ledger Transparency:
 * - Strictly Offline Manual Campus-Counter Receipts (WCI-REC-2026-XXXXX)
 * - Zero Online Payment Gateway • Zero Installment/EMI System
 * - Ed25519 QR Code Scanner for instant paper receipt verification
 */
@Composable
fun ParentFeeLedgerScreen(
    childName: String,
    childRollNo: String,
    totalCourseFeePaisa: Long,
    receipts: List<StudentReceiptItem> = emptyList(),
    onScanReceiptQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPaidPaisa = receipts.sumOf { it.amountPaisa }
    val balancePaisa = (totalCourseFeePaisa - totalPaidPaisa).coerceAtLeast(0L)

    val totalFeeInr = totalCourseFeePaisa / 100
    val totalPaidInr = totalPaidPaisa / 100
    val balanceInr = balancePaisa / 100

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassShaderTokens.BrandNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 20.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Course Fee Transparency Ledger",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Child: $childName • Roll: $childRollNo",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 12.sp
                        )
                    }

                    GlassPill(
                        text = if (balancePaisa == 0L) "FULLY CLEARED" else "BALANCE DUE",
                        color = if (balancePaisa == 0L) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber
                    )
                }
            }

            // Fee Totals Card (Strict Offline Manual Receipts)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 18.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Total Course Fee", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            Text(text = "₹ $totalFeeInr", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Verified Receipts", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            Text(text = "₹ $totalPaidInr", color = GlassShaderTokens.BrandEmerald, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Remaining Due", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            Text(text = "₹ $balanceInr", color = if (balancePaisa == 0L) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandRuby, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Note: Fees are strictly accepted offline at the campus office counter. Online gateways & EMI schemes are prohibited by institute policy.",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassButton(
                        text = "Scan Paper Receipt QR to Verify",
                        onClick = onScanReceiptQr,
                        variant = GlassButtonVariant.SECONDARY,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan",
                                tint = GlassShaderTokens.BrandCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        height = 42.dp
                    )
                }
            }

            // Receipts History
            Text(
                text = "Issued Campus Receipts (${receipts.size})",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (receipts.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 32.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "No receipts",
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No fee receipts on file for this roll number yet",
                            color = Color(0x99FFFFFF),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(receipts) { rec ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 12.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = rec.receiptNo,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Payment Mode: ${rec.paymentMode} • Date: ${rec.dateIso.take(10)}",
                                        color = Color(0x99FFFFFF),
                                        fontSize = 11.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "₹ ${rec.amountPaisa / 100}",
                                        color = GlassShaderTokens.BrandEmerald,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Ed25519 Verified",
                                        tint = GlassShaderTokens.BrandCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
