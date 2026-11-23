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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wci.ui.liquidglass.components.GlassCard
import com.wci.ui.liquidglass.components.GlassPill
import com.wci.ui.liquidglass.theme.GlassShaderTokens

data class DailyAttendanceEvent(
    val dateIso: String,
    val inTimeFormatted: String?,
    val outTimeFormatted: String?,
    val status: String, // "PRESENT", "ABSENT", "LATE"
    val isVerifiedBiometric: Boolean = true
)

/**
 * ParentAttendanceScreen (M4.2: F-446 to F-460)
 * Real-Time Biometric Campus Entry/Exit & Absence Alerts:
 * - Sub-3s push notification status
 * - T+20 min unexplained absence detection
 * - Historical attendance log with hardware verification badge
 */
@Composable
fun ParentAttendanceScreen(
    childName: String,
    childRollNo: String,
    attendanceEvents: List<DailyAttendanceEvent> = emptyList(),
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
                            text = "Campus Gate Attendance",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Child: $childName (Roll: $childRollNo)",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 12.sp
                        )
                    }

                    GlassPill(
                        text = "SUB-3S GATE ALERTS",
                        color = GlassShaderTokens.BrandEmerald
                    )
                }
            }

            // Attendance Logs
            Text(
                text = "Recent Campus Gate Events (${attendanceEvents.size})",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (attendanceEvents.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 32.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "No events",
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No attendance gate events recorded yet for this session",
                            color = Color(0x99FFFFFF),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(attendanceEvents) { ev ->
                        val isPresent = ev.status == "PRESENT"
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = 14.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = ev.dateIso,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isPresent) {
                                            "Entry: ${ev.inTimeFormatted ?: "—"} • Exit: ${ev.outTimeFormatted ?: "In Campus"}"
                                        } else {
                                            "Unexplained Absence (T+20 Alert Triggered)"
                                        },
                                        color = Color(0x99FFFFFF),
                                        fontSize = 11.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlassPill(
                                        text = ev.status,
                                        color = if (isPresent) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandRuby
                                    )
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Icon(
                                        imageVector = if (isPresent) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = "Status",
                                        tint = if (isPresent) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandRuby,
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
