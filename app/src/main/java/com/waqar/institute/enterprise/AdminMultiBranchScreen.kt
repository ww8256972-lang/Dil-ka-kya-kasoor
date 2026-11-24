package com.waqar.institute.enterprise

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InstituteUiState
import com.example.ui.designsystem.JetBrainsMono
import com.example.ui.designsystem.ShimmerButton
import com.example.ui.designsystem.WciColors
import com.example.viewmodel.InstituteViewModel

data class InstituteBranch(
    val branchId: String,
    val branchName: String,
    val city: String,
    val address: String,
    val activeStudents: Int,
    val activeTeachers: Int,
    val status: String = "ACTIVE"
)

/**
 * Multi-Branch & White-Label SaaS Engine (Priority 4.1.1).
 * Allows the Institute Director to manage multiple physical branches,
 * white-label branding configurations, and tenant isolation in Firestore.
 */
@Composable
fun AdminMultiBranchScreen(
    state: InstituteUiState,
    viewModel: InstituteViewModel
) {
    var branchList by remember {
        mutableStateOf(
            listOf(
                InstituteBranch("BR_UNNAO_MAIN", "Waqar Institute - Main Campus", "Unnao", "Civil Lines, Unnao UP", 480, 14, "ACTIVE"),
                InstituteBranch("BR_KANPUR_EAST", "Waqar Institute - Tech City", "Kanpur", "Kakadeo, Kanpur UP", 320, 10, "ACTIVE"),
                InstituteBranch("BR_LUCKNOW_EXT", "Waqar Institute - Digital Hub", "Lucknow", "Hazratganj, Lucknow UP", 210, 6, "PROVISIONING")
            )
        )
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }
    var newCity by remember { mutableStateOf("") }
    var newAddress by remember { mutableStateOf("") }

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
                        text = "Multi-Branch & White-Label SaaS Engine",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Manage multi-tenant campuses, regional branding, domain isolates, and synchronized Cloud Firestore instances across branches.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = WciColors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ShimmerButton(
                        text = "Add New Regional Branch",
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        item {
            Text(
                text = "Active Regional Campuses (${branchList.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = WciColors.textPrimary
                )
            )
        }

        items(branchList) { branch ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WciColors.elevatedSurface),
                border = BorderStroke(1.dp, WciColors.IndigoAccent.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = branch.branchName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (branch.status == "ACTIVE") WciColors.Success.copy(alpha = 0.2f) else WciColors.Warning.copy(alpha = 0.2f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = branch.status,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (branch.status == "ACTIVE") WciColors.Success else WciColors.Warning
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${branch.city} • ${branch.address}",
                        style = MaterialTheme.typography.bodySmall.copy(color = WciColors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "${branch.activeStudents} Students",
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            color = WciColors.infoColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${branch.activeTeachers} Faculty Members",
                            fontFamily = JetBrainsMono,
                            fontSize = 12.sp,
                            color = WciColors.accentCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Register New Branch", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        label = { Text("Branch Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newCity,
                        onValueChange = { newCity = it },
                        label = { Text("City") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAddress,
                        onValueChange = { newAddress = it },
                        label = { Text("Address") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newBranchName.isNotBlank() && newCity.isNotBlank()) {
                            val newB = InstituteBranch(
                                branchId = "BR_${newCity.uppercase().take(6)}_${System.currentTimeMillis() % 1000}",
                                branchName = newBranchName,
                                city = newCity,
                                address = newAddress.ifBlank { "$newCity Campus" },
                                activeStudents = 0,
                                activeTeachers = 0,
                                status = "PROVISIONING"
                            )
                            branchList = branchList + newB
                            showAddDialog = false
                            newBranchName = ""
                            newCity = ""
                            newAddress = ""
                        }
                    }
                ) {
                    Text("Register Branch", color = WciColors.Success)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = WciColors.Danger)
                }
            },
            containerColor = WciColors.elevatedSurface
        )
    }
}
