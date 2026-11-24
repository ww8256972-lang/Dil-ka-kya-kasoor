package com.waqar.institute.enterprise

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InstituteUiState
import com.example.ui.designsystem.JetBrainsMono
import com.example.ui.designsystem.WciColors
import com.example.viewmodel.InstituteViewModel

/**
 * Interactive 3D/AR Hardware & Networking Lab (Priority 4.3.1).
 * 100% Native Jetpack Compose Canvas simulation for virtual PC assembly,
 * motherboard CPU/RAM socketing, and RJ45 Ethernet switch cabling simulations.
 */
@Composable
fun StudentVirtualLabScreen(
    state: InstituteUiState,
    viewModel: InstituteViewModel
) {
    var selectedComponent by remember { mutableStateOf("RAM DDR4") }
    var isRamInstalled by remember { mutableStateOf(false) }
    var isCpuInstalled by remember { mutableStateOf(false) }
    var isGpuInstalled by remember { mutableStateOf(false) }
    var isNetworkCableConnected by remember { mutableStateOf(false) }

    var cableEndOffset by remember { mutableStateOf(Offset(120f, 200f)) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = WciColors.containerSurface),
                border = BorderStroke(1.dp, WciColors.GlassBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Interactive Hardware & Networking Lab",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Hands-on virtual assembly arena: Seat CPU/RAM into sockets, route Cat6 patch cables, and verify circuit connectivity.",
                        style = MaterialTheme.typography.bodySmall.copy(color = WciColors.textSecondary)
                    )
                }
            }
        }

        item {
            Text(
                text = "Interactive Assembly Canvas (Drag & Connect)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = WciColors.textPrimary
                )
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.5.dp, WciColors.accentCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    cableEndOffset += dragAmount
                                    if (cableEndOffset.x in 450f..650f && cableEndOffset.y in 100f..250f) {
                                        isNetworkCableConnected = true
                                    }
                                }
                            }
                    ) {
                        // Motherboard PCB Outline
                        drawRoundRect(
                            color = Color(0xFF064E3B),
                            topLeft = Offset(40f, 30f),
                            size = androidx.compose.ui.geometry.Size(size.width - 80f, size.height - 60f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                        )

                        // CPU Socket (LGA 1700)
                        val cpuColor = if (isCpuInstalled) Color(0xFF10B981) else Color(0xFF475569)
                        drawRect(
                            color = cpuColor,
                            topLeft = Offset(80f, 60f),
                            size = androidx.compose.ui.geometry.Size(90f, 90f)
                        )

                        // RAM DIMM Slots
                        val ramColor = if (isRamInstalled) Color(0xFF06B6D4) else Color(0xFF334155)
                        drawRect(
                            color = ramColor,
                            topLeft = Offset(200f, 50f),
                            size = androidx.compose.ui.geometry.Size(20f, 110f)
                        )
                        drawRect(
                            color = ramColor,
                            topLeft = Offset(230f, 50f),
                            size = androidx.compose.ui.geometry.Size(20f, 110f)
                        )

                        // PCIe x16 Slot (GPU)
                        val gpuColor = if (isGpuInstalled) Color(0xFF8B5CF6) else Color(0xFF334155)
                        drawRect(
                            color = gpuColor,
                            topLeft = Offset(80f, 175f),
                            size = androidx.compose.ui.geometry.Size(180f, 25f)
                        )

                        // RJ45 Ethernet Port Jack
                        drawRect(
                            color = if (isNetworkCableConnected) Color(0xFF10B981) else Color(0xFFF59E0B),
                            topLeft = Offset(size.width - 120f, 60f),
                            size = androidx.compose.ui.geometry.Size(50f, 50f)
                        )

                        // Cat6 Patch Cable Line
                        drawLine(
                            color = if (isNetworkCableConnected) Color(0xFF10B981) else Color(0xFF06B6D4),
                            start = Offset(60f, 220f),
                            end = if (isNetworkCableConnected) Offset(size.width - 95f, 85f) else cableEndOffset,
                            strokeWidth = 6f,
                            pathEffect = if (!isNetworkCableConnected) PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f) else null
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = if (isNetworkCableConnected) "✓ Gigabit Ethernet Link Connected" else "Drag Cat6 Patch Cable to Switch Jack",
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            color = if (isNetworkCableConnected) WciColors.Success else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Hardware Components Tray (Tap to Slot)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = WciColors.textPrimary
                )
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { isCpuInstalled = !isCpuInstalled },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isCpuInstalled) WciColors.Success else Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isCpuInstalled) "CPU ✓" else "+ Slot CPU", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { isRamInstalled = !isRamInstalled },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isRamInstalled) WciColors.Success else Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isRamInstalled) "RAM ✓" else "+ Slot RAM", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { isGpuInstalled = !isGpuInstalled },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isGpuInstalled) WciColors.Success else Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isGpuInstalled) "GPU ✓" else "+ Slot GPU", fontSize = 12.sp)
                }
            }
        }
    }
}
