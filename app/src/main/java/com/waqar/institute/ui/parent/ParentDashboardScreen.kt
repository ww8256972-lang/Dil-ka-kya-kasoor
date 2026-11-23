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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

data class ChildSummary(
    val rollNo: String,
    val name: String,
    val courseName: String,
    val attendancePercent: Int,
    val isPresentToday: Boolean,
    val feeBalancePaisa: Long
)

/**
 * ParentDashboardScreen (M4.1: F-431 to F-445)
 * Guardian Executive Dashboard & Multi-Child Switcher:
 * - Child profile switcher
 * - Real-time campus entry/exit status
 * - Attendance summary & quick action links
 */
@Composable
fun ParentDashboardScreen(
    guardianName: String,
    children: List<ChildSummary> = emptyList(),
    onNavigateToAttendance: (rollNo: String) -> Unit,
    onNavigateToFees: (rollNo: String) -> Unit,
    onNavigateToResults: (rollNo: String) -> Unit,
    onNavigateToChat: (rollNo: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedChildIndex by remember { mutableIntStateOf(0) }
    val currentChild = children.getOrNull(selectedChildIndex)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassShaderTokens.BrandNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 20.dp
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Namaste, $guardianName",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Parent / Guardian Portal • Unnao Main Campus",
                                color = GlassShaderTokens.BrandCyan,
                                fontSize = 12.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.FamilyRestroom,
                            contentDescription = "Family",
                            tint = GlassShaderTokens.BrandPurple,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Multi-Child Switcher (F-431)
            if (children.size > 1) {
                Text(
                    text = "Select Child (${children.size} Enrolled)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(children) { idx, child ->
                        val isSelected = idx == selectedChildIndex
                        GlassButton(
                            text = child.name,
                            onClick = { selectedChildIndex = idx },
                            variant = if (isSelected) GlassButtonVariant.PRIMARY else GlassButtonVariant.SECONDARY,
                            height = 38.dp
                        )
                    }
                }
            }

            // Selected Child Overview Card
            if (currentChild != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassShaderTokens.BrandMidnight,
                    elevation = 8.dp,
                    contentPadding = 18.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = currentChild.name,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Roll: ${currentChild.rollNo} • ${currentChild.courseName}",
                                    color = GlassShaderTokens.BrandCyan,
                                    fontSize = 12.sp
                                )
                            }

                            GlassPill(
                                text = if (currentChild.isPresentToday) "ON CAMPUS TODAY" else "ABSENT / PENDING",
                                color = if (currentChild.isPresentToday) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Attendance", color = Color(0x99FFFFFF), fontSize = 11.sp)
                                Text(
                                    text = "${currentChild.attendancePercent}%",
                                    color = if (currentChild.attendancePercent >= 75) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Fee Due", color = Color(0x99FFFFFF), fontSize = 11.sp)
                                Text(
                                    text = "₹ ${currentChild.feeBalancePaisa / 100}",
                                    color = if (currentChild.feeBalancePaisa == 0L) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandRuby,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Quick Navigation Hub
                Text(
                    text = "Guardian Oversight Modules",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "Attendance",
                        onClick = { onNavigateToAttendance(currentChild.rollNo) },
                        variant = GlassButtonVariant.PRIMARY,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = "Attendance",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        height = 50.dp
                    )

                    GlassButton(
                        text = "Fee Receipts",
                        onClick = { onNavigateToFees(currentChild.rollNo) },
                        variant = GlassButtonVariant.ACCENT,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = "Fees",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        height = 50.dp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GlassButton(
                        text = "Exam Results",
                        onClick = { onNavigateToResults(currentChild.rollNo) },
                        variant = GlassButtonVariant.SECONDARY,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Results",
                                tint = GlassShaderTokens.BrandPurple,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        height = 50.dp
                    )

                    GlassButton(
                        text = "Teacher Chat",
                        onClick = { onNavigateToChat(currentChild.rollNo) },
                        variant = GlassButtonVariant.SECONDARY,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "Chat",
                                tint = GlassShaderTokens.BrandCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        height = 50.dp
                    )
                }
            } else {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 32.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No linked student records found for this guardian account",
                            color = Color(0x99FFFFFF),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
