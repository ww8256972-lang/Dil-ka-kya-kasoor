package com.waqar.institute.enterprise

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InstituteUiState
import com.example.ui.designsystem.JetBrainsMono
import com.example.ui.designsystem.ShimmerButton
import com.example.ui.designsystem.WciColors
import com.example.viewmodel.InstituteViewModel

data class LeadEnquiry(
    val id: String,
    val studentName: String,
    val phone: String,
    val interestedCourse: String,
    val stage: String, // INQUIRY, DEMO_BOOKED, ADMITTED
    val aiScore: Int,
    val lastFollowUp: String
)

/**
 * AI Admission & Calling CRM (Priority 4.1.3).
 * Lead pipeline tracker from first inquiry to live demo lab booking and final admission confirmation,
 * complete with one-tap WhatsApp and Call trigger logs.
 */
@Composable
fun AdminCallingCrmScreen(
    state: InstituteUiState,
    viewModel: InstituteViewModel
) {
    val context = LocalContext.current
    var leads by remember {
        mutableStateOf(
            listOf(
                LeadEnquiry("LEAD-101", "Rohan Sharma", "+919876543210", "DCA & Python Programming", "DEMO_BOOKED", 92, "Today, 11:30 AM"),
                LeadEnquiry("LEAD-102", "Priya Verma", "+919812345678", "ADCA & Tally Prime GST", "INQUIRY", 78, "Yesterday"),
                LeadEnquiry("LEAD-103", "Aman Siddiqui", "+919765432109", "Web Development & MERN", "ADMITTED", 98, "Completed"),
                LeadEnquiry("LEAD-104", "Kavita Yadav", "+919988776655", "CCC & Typing Speed Pro", "INQUIRY", 64, "2 days ago")
            )
        )
    }

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
                        text = "AI Admission & Calling CRM",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Real-time AI lead qualification, demo lab attendance scheduler, and direct WhatsApp CRM integration.",
                        style = MaterialTheme.typography.bodySmall.copy(color = WciColors.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = WciColors.infoColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, WciColors.infoColor.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Inquiries", fontSize = 11.sp, color = WciColors.textSecondary)
                                Text("18", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = WciColors.Warning.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, WciColors.Warning.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Demo Booked", fontSize = 11.sp, color = WciColors.textSecondary)
                                Text("9", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WciColors.Warning)
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = WciColors.Success.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, WciColors.Success.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Confirmed", fontSize = 11.sp, color = WciColors.textSecondary)
                                Text("12", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WciColors.Success)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Prospect Pipeline (${leads.size})",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = WciColors.textPrimary
                )
            )
        }

        items(leads) { lead ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = WciColors.elevatedSurface),
                border = BorderStroke(1.dp, WciColors.GlassBorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(lead.studentName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                            Text(lead.interestedCourse, fontSize = 12.sp, color = WciColors.accentCyan)
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    when (lead.stage) {
                                        "ADMITTED" -> WciColors.Success.copy(alpha = 0.2f)
                                        "DEMO_BOOKED" -> WciColors.Warning.copy(alpha = 0.2f)
                                        else -> WciColors.infoColor.copy(alpha = 0.2f)
                                    },
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = lead.stage,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (lead.stage) {
                                    "ADMITTED" -> WciColors.Success
                                    "DEMO_BOOKED" -> WciColors.Warning
                                    else -> WciColors.infoColor
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "AI Intent: ${lead.aiScore}% • Follow-up: ${lead.lastFollowUp}",
                            fontFamily = JetBrainsMono,
                            fontSize = 11.5.sp,
                            color = WciColors.textSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = {
                                    runCatching {
                                        val clean = lead.phone.replace("+", "").trim()
                                        val uri = Uri.parse("https://wa.me/$clean?text=Hello%20${lead.studentName},%20Greetings%20from%20Waqar%20Computer%20Institute!%20Your%20demo%20lab%20seat%20is%20ready.")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = WciColors.Success)
                            }
                            IconButton(
                                onClick = {
                                    runCatching {
                                        val uri = Uri.parse("tel:${lead.phone}")
                                        context.startActivity(Intent(Intent.ACTION_DIAL, uri))
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call", tint = WciColors.accentCyan)
                            }
                        }
                    }
                }
            }
        }
    }
}
