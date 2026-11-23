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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
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

data class FacultyProfileData(
    val teacherUid: String,
    val fullName: String,
    val designation: String,
    val department: String,
    val joiningDate: String,
    val monthlySalaryPaisa: Long,
    val attendanceDaysPresent: Int,
    val casualLeaveBalance: Int,
    val medicalLeaveBalance: Int
)

/**
 * TeacherProfileScreen (M2.8: F-297 to F-310)
 * Teacher Profile, Biometric Attendance History & Monthly Payroll Slip:
 * - Hardware Biometric check-in log
 * - Salary breakdown in Paise / INR
 * - Leave ledger management
 */
@Composable
fun TeacherProfileScreen(
    profile: FacultyProfileData,
    onApplyLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val salaryInr = profile.monthlySalaryPaisa / 100

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
            // Profile Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 20.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(GlassShaderTokens.ActiveGlowGradient, shape = androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Faculty Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.size(16.dp))

                    Column {
                        Text(
                            text = profile.fullName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${profile.designation} • ${profile.department}",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Faculty ID: ${profile.teacherUid} • Joined: ${profile.joiningDate}",
                            color = Color(0x99FFFFFF),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Attendance & Cutoff Card
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
                            text = "Biometric Campus Attendance",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        GlassPill(
                            text = "${profile.attendanceDaysPresent} DAYS PRESENT",
                            color = GlassShaderTokens.BrandEmerald
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Daily check-in gate enforces 10:30 AM IST cutoff at Unnao Main Campus. Late entries automatically flag supervisor review.",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            // Monthly Salary Slip Card
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = "Salary",
                                tint = GlassShaderTokens.BrandEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Monthly Disbursed Payroll",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "₹ $salaryInr",
                            color = GlassShaderTokens.BrandEmerald,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Direct Bank NEFT / Campus Payroll Transfer • Form 16 & TDS compliant",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            // Leave Balances Card
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
                            text = "Leave Ledger Balance",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        GlassButton(
                            text = "Apply Leave",
                            onClick = onApplyLeave,
                            variant = GlassButtonVariant.SECONDARY,
                            height = 32.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            contentPadding = 12.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Casual Leave", color = Color(0x99FFFFFF), fontSize = 11.sp)
                                Text(
                                    text = "${profile.casualLeaveBalance} Days",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        GlassCard(
                            modifier = Modifier.weight(1f),
                            contentPadding = 12.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "Medical Leave", color = Color(0x99FFFFFF), fontSize = 11.sp)
                                Text(
                                    text = "${profile.medicalLeaveBalance} Days",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
