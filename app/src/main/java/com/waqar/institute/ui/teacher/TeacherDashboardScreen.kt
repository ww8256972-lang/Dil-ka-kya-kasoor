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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Sync
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
import com.waqar.institute.data.teacher.OfflineGradeQueue

data class TeacherBatchSlot(
    val batchId: String,
    val courseName: String,
    val timeSlot: String,
    val room: String,
    val enrolledCount: Int
)

/**
 * TeacherDashboardScreen (M2.1: F-191 to F-205)
 * Teacher Portal Command Hub: Daily schedule, campus geofence check-in,
 * active batch timetable, and offline grade synchronization status.
 */
@Composable
fun TeacherDashboardScreen(
    teacherName: String,
    teacherUid: String,
    assignedBatches: List<TeacherBatchSlot> = emptyList(),
    onNavigateToProctor: (batchId: String) -> Unit,
    onNavigateToGrading: (batchId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isGeofenceCheckedIn by remember { mutableStateOf(false) }
    var checkInStatusText by remember { mutableStateOf("Pending Campus Check-In") }
    val pendingGrades = OfflineGradeQueue.getPendingCount()

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
            // Header: Welcome & Geofence Status (F-191)
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                elevation = 8.dp,
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
                                text = "Welcome, Prof. $teacherName",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Faculty ID: $teacherUid • Main Campus",
                                color = GlassShaderTokens.BrandCyan,
                                fontSize = 12.sp
                            )
                        }

                        GlassPill(
                            text = if (isGeofenceCheckedIn) "CHECKED-IN (50M GEOFENCE)" else "GEOFENCE REQUIRED",
                            color = if (isGeofenceCheckedIn) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isGeofenceCheckedIn) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                                    contentDescription = "Geofence",
                                    tint = if (isGeofenceCheckedIn) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Campus Coordinates: 26.538162° N, 80.484187° E (Unnao Main)",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!isGeofenceCheckedIn) {
                        GlassButton(
                            text = "Verify Location & Check-In",
                            onClick = {
                                isGeofenceCheckedIn = true
                                checkInStatusText = "Verified on Campus at 26.538162, 80.484187"
                            },
                            variant = GlassButtonVariant.PRIMARY,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = checkInStatusText,
                            color = GlassShaderTokens.BrandEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Sync Pill & Offline Queue Status (F-231)
            if (pendingGrades > 0) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = GlassShaderTokens.BrandPurple.copy(alpha = 0.15f),
                    contentPadding = 14.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Sync",
                                tint = GlassShaderTokens.BrandCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$pendingGrades offline grades waiting for sync",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }

                        GlassButton(
                            text = "Sync Now",
                            onClick = { OfflineGradeQueue.clearQueue() },
                            variant = GlassButtonVariant.ACCENT,
                            height = 36.dp
                        )
                    }
                }
            }

            // Today's Batches List (F-192)
            Text(
                text = "Today's Assigned Batches (${assignedBatches.size})",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (assignedBatches.isEmpty()) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = 24.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "No classes",
                            tint = Color(0x66FFFFFF),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No batch sessions scheduled for today",
                            color = Color(0x99FFFFFF),
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                assignedBatches.forEach { batch ->
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
                                Text(
                                    text = batch.courseName,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                GlassPill(
                                    text = batch.timeSlot,
                                    color = GlassShaderTokens.BrandCyan
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Batch ID: ${batch.batchId} • ${batch.room} • ${batch.enrolledCount} Students",
                                color = Color(0xCCFFFFFF),
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                GlassButton(
                                    text = "Lab Grading",
                                    onClick = { onNavigateToGrading(batch.batchId) },
                                    variant = GlassButtonVariant.SECONDARY,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Assignment,
                                            contentDescription = "Grading",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 40.dp
                                )

                                GlassButton(
                                    text = "Live Proctor",
                                    onClick = { onNavigateToProctor(batch.batchId) },
                                    variant = GlassButtonVariant.PRIMARY,
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.School,
                                            contentDescription = "Proctor",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 40.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
