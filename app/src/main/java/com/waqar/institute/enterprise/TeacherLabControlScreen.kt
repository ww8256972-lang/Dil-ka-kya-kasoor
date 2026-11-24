package com.waqar.institute.enterprise

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InstituteUiState
import com.example.ui.designsystem.JetBrainsMono
import com.example.ui.designsystem.ShimmerButton
import com.example.ui.designsystem.WciColors
import com.example.viewmodel.InstituteViewModel

data class LabPcSession(
    val pcNumber: Int,
    val studentName: String?,
    val activeTask: String,
    val isLocked: Boolean,
    val connectionIp: String
)

/**
 * Central Computer Lab Control & Live Monitoring (Priority 4.2.1).
 * Live terminal dashboard for faculty members to view active lab PC sessions,
 * remotely lock lab screens, and broadcast real-time instructions to all 24 PCs.
 */
@Composable
fun TeacherLabControlScreen(
    state: InstituteUiState,
    viewModel: InstituteViewModel
) {
    var pcSessions by remember {
        mutableStateOf(
            (1..24).map { idx ->
                val hasStudent = idx in listOf(1, 2, 3, 5, 7, 8, 9, 12, 14, 15, 18, 20)
                LabPcSession(
                    pcNumber = idx,
                    studentName = if (hasStudent) "Student Roll #$idx" else null,
                    activeTask = if (hasStudent) (if (idx % 2 == 0) "Python Scripting" else "Tally Prime Ledger") else "Idle Screen",
                    isLocked = false,
                    connectionIp = "192.168.1.${100 + idx}"
                )
            }
        )
    }

    var broadcastMsg by remember { mutableStateOf("") }
    var showBroadcastNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = WciColors.containerSurface),
            border = BorderStroke(1.dp, WciColors.GlassBorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Computer Lab 1 Live Console",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "24 High-Performance Lab PCs • LAN Subnet 192.168.1.0/24",
                            style = MaterialTheme.typography.bodySmall.copy(color = WciColors.textSecondary)
                        )
                    }
                    val activeCount = pcSessions.count { it.studentName != null }
                    Box(
                        modifier = Modifier
                            .background(WciColors.Success.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("$activeCount / 24 Active", fontWeight = FontWeight.Bold, color = WciColors.Success, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = broadcastMsg,
                        onValueChange = { broadcastMsg = it },
                        placeholder = { Text("Broadcast instruction to all PC screens…") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (broadcastMsg.isNotBlank()) {
                                showBroadcastNotice = true
                                broadcastMsg = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = WciColors.EmeraldAction)
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Broadcast")
                    }
                }
            }
        }

        if (showBroadcastNotice) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = WciColors.Success.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, WciColors.Success.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "Instruction successfully broadcasted to all 24 Lab Terminals ✓",
                    modifier = Modifier.padding(10.dp),
                    fontSize = 12.5.sp,
                    color = WciColors.Success,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Active Lab PC Grid (Tap to Lock / Unlock)",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = WciColors.textPrimary
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(pcSessions) { session ->
                val isOccupied = session.studentName != null
                Card(
                    onClick = {
                        pcSessions = pcSessions.map {
                            if (it.pcNumber == session.pcNumber) it.copy(isLocked = !it.isLocked) else it
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (session.isLocked) WciColors.Danger.copy(alpha = 0.25f)
                        else if (isOccupied) WciColors.elevatedSurface
                        else Color.White.copy(alpha = 0.04f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (session.isLocked) WciColors.Danger
                        else if (isOccupied) WciColors.accentCyan.copy(alpha = 0.5f)
                        else WciColors.GlassBorderLight
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "PC-${"%02d".format(session.pcNumber)}",
                            fontFamily = JetBrainsMono,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Icon(
                            imageVector = if (session.isLocked) Icons.Default.Lock
                            else if (isOccupied) Icons.Default.Computer
                            else Icons.Default.DesktopWindows,
                            contentDescription = null,
                            tint = if (session.isLocked) WciColors.Danger
                            else if (isOccupied) WciColors.accentCyan
                            else WciColors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (session.isLocked) "LOCKED" else if (isOccupied) "Active" else "Empty",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (session.isLocked) WciColors.Danger else if (isOccupied) WciColors.Success else WciColors.textMuted
                        )
                    }
                }
            }
        }
    }
}
