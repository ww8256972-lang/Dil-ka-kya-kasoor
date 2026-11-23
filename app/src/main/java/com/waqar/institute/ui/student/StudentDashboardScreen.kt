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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
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

data class StudentDashboardData(
    val rollNo: String,
    val fullName: String,
    val courseName: String,
    val batchName: String,
    val attendancePercent: Int,
    val syllabusPercent: Int,
    val isFeeClear: Boolean
)

/**
 * StudentDashboardScreen (M3.1: F-311 to F-325)
 * Student Learning Command Center:
 * - Attendance & Syllabus progress rings
 * - Today's timetable
 * - Quick-launch docks for CBT Exam Arena, Coding Sandbox, Tally Lab, and AI Tutor
 */
@Composable
fun StudentDashboardScreen(
    data: StudentDashboardData,
    onLaunchExam: () -> Unit,
    onLaunchCoding: () -> Unit,
    onLaunchTally: () -> Unit,
    onLaunchAiTutor: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                                text = "Welcome, ${data.fullName}",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Roll: ${data.rollNo} • ${data.courseName}",
                                color = GlassShaderTokens.BrandCyan,
                                fontSize = 12.sp
                            )
                        }

                        GlassPill(
                            text = if (data.isFeeClear) "FEE VERIFIED" else "PENDING RECEIPT",
                            color = if (data.isFeeClear) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Batch: ${data.batchName} • Main Campus (Unnao, UP)",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            // Progress Summary Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    contentPadding = 14.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Attendance", color = Color(0x99FFFFFF), fontSize = 11.sp)
                        Text(
                            text = "${data.attendancePercent}%",
                            color = if (data.attendancePercent >= 75) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "75% Minimum Required", color = Color(0x66FFFFFF), fontSize = 9.sp)
                    }
                }

                GlassCard(
                    modifier = Modifier.weight(1f),
                    contentPadding = 14.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "Curriculum", color = Color(0x99FFFFFF), fontSize = 11.sp)
                        Text(
                            text = "${data.syllabusPercent}%",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Modules Completed", color = Color(0x66FFFFFF), fontSize = 9.sp)
                    }
                }
            }

            // Practical Docks Grid
            Text(
                text = "Academic & Practical Learning Hub",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "CBT Exam Arena",
                    onClick = onLaunchExam,
                    variant = GlassButtonVariant.PRIMARY,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = "CBT",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    height = 56.dp
                )

                GlassButton(
                    text = "Coding Lab",
                    onClick = onLaunchCoding,
                    variant = GlassButtonVariant.ACCENT,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Coding",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    height = 56.dp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GlassButton(
                    text = "Tally Simulator",
                    onClick = onLaunchTally,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Tally",
                            tint = GlassShaderTokens.BrandEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    height = 56.dp
                )

                GlassButton(
                    text = "AI Study Tutor",
                    onClick = onLaunchAiTutor,
                    variant = GlassButtonVariant.SECONDARY,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = GlassShaderTokens.BrandPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    height = 56.dp
                )
            }
        }
    }
}
