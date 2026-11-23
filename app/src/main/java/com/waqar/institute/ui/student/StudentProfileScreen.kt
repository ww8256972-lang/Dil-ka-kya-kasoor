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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
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

data class StudentProfileData(
    val rollNo: String,
    val fullName: String,
    val fatherName: String,
    val phone: String,
    val courseName: String,
    val admissionDate: String,
    val isBiometricEnrolled: Boolean,
    val unreadNotificationsCount: Int = 0
)

/**
 * StudentProfileScreen (M3.8: F-417 to F-430)
 * Student Profile, Biometric Enclave Status & Notifications:
 * - Hardware Biometric Face Mesh template registration
 * - Student lifecycle credentials
 * - Single-campus Unnao Main certification
 */
@Composable
fun StudentProfileScreen(
    profile: StudentProfileData,
    onLaunchBiometricEnrollment: () -> Unit,
    onOpenNotifications: () -> Unit,
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
                            .background(GlassShaderTokens.ActiveGlowGradient, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
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
                            text = "Roll: ${profile.rollNo} • ${profile.courseName}",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Admitted: ${profile.admissionDate} • Guardian: ${profile.fatherName}",
                            color = Color(0x99FFFFFF),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Hardware Biometric Enclave Card (S-36, S-50)
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
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric",
                                tint = GlassShaderTokens.BrandCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Biometric Face Gate (S-50)",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        GlassPill(
                            text = if (profile.isBiometricEnrolled) "ENROLLED IN TEE" else "ENROLLMENT REQUIRED",
                            color = if (profile.isBiometricEnrolled) GlassShaderTokens.BrandEmerald else GlassShaderTokens.BrandAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "512-dimensional face template is encrypted with Google Tink (S-13) and sealed inside StrongBox hardware for CBT exam verification.",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp
                    )

                    if (!profile.isBiometricEnrolled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        GlassButton(
                            text = "Launch Biometric Face Enrollment",
                            onClick = onLaunchBiometricEnrollment,
                            variant = GlassButtonVariant.PRIMARY,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Face,
                                    contentDescription = "Face",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Notification Hub Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 18.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Alerts",
                            tint = GlassShaderTokens.BrandPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Column {
                            Text(
                                text = "Campus Notice & Alert Hub",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${profile.unreadNotificationsCount} unread notices",
                                color = Color(0x99FFFFFF),
                                fontSize = 11.sp
                            )
                        }
                    }

                    GlassButton(
                        text = "View Notices",
                        onClick = onOpenNotifications,
                        variant = GlassButtonVariant.SECONDARY,
                        height = 36.dp
                    )
                }
            }

            // Security & Campus Hard-Lock Notice
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 14.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Lock",
                        tint = GlassShaderTokens.BrandEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "Enrolled at Waqar Computer Institute Main Campus (188 DSN College Marg, Unnao). Pure Native Android security active.",
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
