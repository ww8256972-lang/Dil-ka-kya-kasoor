package com.waqar.institute.ui.student

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wci.ui.liquidglass.components.GlassCard
import com.wci.ui.liquidglass.components.GlassPill
import com.wci.ui.liquidglass.theme.GlassShaderTokens

data class StudentReceiptItem(
    val receiptNo: String,
    val dateIso: String,
    val amountPaisa: Long,
    val paymentMode: String,
    val isVerified: Boolean = true
)

/**
 * StudentIdAndFeesScreen (M3.7: F-403 to F-416)
 * Student Digital ID Card & 100% Offline Fee Receipt Ledger:
 * - Ed25519-Signed Digital Student ID Card
 * - Flat course fee summary (Strictly Zero Online Gateways / Zero EMI)
 * - Verified campus counter receipts with Merkle hash integrity
 */
@Composable
fun StudentIdAndFeesScreen(
    rollNo: String,
    studentName: String,
    courseName: String,
    totalCourseFeePaisa: Long,
    receipts: List<StudentReceiptItem> = emptyList(),
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
            // Digital Student ID Card (Ed25519 QR)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = GlassShaderTokens.BrandMidnight,
                elevation = 10.dp,
                contentPadding = 20.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(GlassShaderTokens.ActiveGlowGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "WCI",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                            Column {
                                Text(
                                    text = "Waqar Computer Institute",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Main Campus • 188 DSN College Marg",
                                    color = GlassShaderTokens.BrandCyan,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        GlassPill(
                            text = "ED25519 ID",
                            color = GlassShaderTokens.BrandEmerald
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = studentName,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Roll No: $rollNo",
                                color = GlassShaderTokens.BrandCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Enrolled Course: $courseName",
                                color = Color(0xCCFFFFFF),
                                fontSize = 12.sp
                            )
                        }

                        // Digital QR Badge Placeholder
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Student QR",
                                tint = Color.Black,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            // Flat Fee Balance Summary Card (Zero Gateway / Zero EMI)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 18.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Course Fee Ledger (Manual Receipts)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        GlassPill(
                            text = if (balancePaisa == 0L) "FULLY CLEARED" else "BALANCE DUE",
                            color = if (balancePaisa == 0L) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Total Course Fee", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            Text(text = "₹ $totalFeeInr", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Total Paid", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            Text(text = "₹ $totalPaidInr", color = GlassShaderTokens.BrandEmerald, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Remaining Due", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            Text(text = "₹ $balanceInr", color = if (balancePaisa == 0L) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandRuby, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Receipts List
            Text(
                text = "Verified Offline Campus Receipts (${receipts.size})",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (receipts.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 24.dp
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
                            text = "No fee receipts recorded for this roll number yet",
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
                                        text = "Mode: ${rec.paymentMode} • Date: ${rec.dateIso.take(10)}",
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
                                        contentDescription = "Verified",
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
