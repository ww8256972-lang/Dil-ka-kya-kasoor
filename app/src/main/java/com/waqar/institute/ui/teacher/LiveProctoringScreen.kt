package com.waqar.institute.ui.teacher

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

data class ProctorStudentState(
    val studentRollNo: String,
    val studentName: String,
    val currentQuestionIndex: Int,
    val totalQuestions: Int = 100,
    val isKioskPinned: Boolean,
    val anomalyCount: Int,
    val isBufferingOffline: Boolean,
    val lastPingSecondsAgo: Int
)

/**
 * LiveProctoringScreen (M2.3: F-223 to F-236)
 * Real-time biometric proctor command center:
 * - Monitors Android Lock-Task kiosk pinning status
 * - Displays ML Kit face anomaly warnings (multi-face, eye gazing)
 * - Identifies offline SQLCipher answer buffering vs active Firestore sync (F-228)
 */
@Composable
fun LiveProctoringScreen(
    examId: String,
    activeExaminees: List<ProctorStudentState> = emptyList(),
    onTerminateStudentExam: (rollNo: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStudentForAction by remember { mutableStateOf<ProctorStudentState?>(null) }

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
                            text = "Live Proctor Command Center",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Exam ID: $examId • Main Campus CBT Lab",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 12.sp
                        )
                    }

                    GlassPill(
                        text = "${activeExaminees.size} ACTIVE EXAMINEES",
                        color = GlassShaderTokens.BrandEmerald
                    )
                }
            }

            // Examinees List
            if (activeExaminees.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 32.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "No Examinees",
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No live students currently logged into this exam session",
                            color = Color(0x99FFFFFF),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(activeExaminees, key = { it.studentRollNo }) { student ->
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 16.dp
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = student.studentName,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Roll: ${student.studentRollNo} • Q ${student.currentQuestionIndex + 1}/${student.totalQuestions}",
                                            color = Color(0xCCFFFFFF),
                                            fontSize = 12.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (student.isBufferingOffline) {
                                            GlassPill(
                                                text = "OFFLINE SQLCIPHER",
                                                color = GlassShaderTokens.BrandAmber
                                            )
                                        } else {
                                            GlassPill(
                                                text = "ONLINE SYNC",
                                                color = GlassShaderTokens.BrandEmerald
                                            )
                                        }

                                        if (student.anomalyCount > 0) {
                                            GlassPill(
                                                text = "${student.anomalyCount} ANOMALIES",
                                                color = GlassShaderTokens.BrandRuby,
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = "Alert",
                                                        tint = GlassShaderTokens.BrandRuby,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Kiosk",
                                            tint = if (student.isKioskPinned) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandRuby,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (student.isKioskPinned) "Kiosk Lock-Task Active" else "KIOSK PINNING VIOLATION",
                                            color = if (student.isKioskPinned) Color(0xCCFFFFFF) else GlassShaderTokens.BrandRuby,
                                            fontSize = 11.sp
                                        )
                                    }

                                    GlassButton(
                                        text = "Force Submit",
                                        onClick = { onTerminateStudentExam(student.studentRollNo) },
                                        variant = GlassButtonVariant.DANGER,
                                        height = 32.dp
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
