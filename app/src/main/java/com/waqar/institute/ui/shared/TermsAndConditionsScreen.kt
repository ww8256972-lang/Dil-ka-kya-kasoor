package com.waqar.institute.ui.shared

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

data class InstituteRule(
    val ruleNumber: Int,
    val title: String,
    val description: String,
    val ruleCategory: String
)

val WCI_TEN_RULES = listOf(
    InstituteRule(
        ruleNumber = 1,
        title = "Strict Single-Campus Enrollment & Jurisdiction",
        description = "All academic programs, laboratory sessions, and examinations are administered exclusively at Waqar Computer Institute Main Campus (188, DSN College Marg, Ab Nagar, Unnao, Uttar Pradesh 209801). All legal and institutional governance falls under Unnao jurisdiction.",
        ruleCategory = "CAMPUS"
    ),
    InstituteRule(
        ruleNumber = 2,
        title = "Offline Campus-Counter Fee Settlement Only",
        description = "Course fees are payable exclusively at the official campus accounts counter via manual entry. No online payment gateways, payment links, UPI intent, or installment/EMI schemes are supported. Valid receipts require an authentic Ed25519 cryptographic counter signature.",
        ruleCategory = "FINANCE"
    ),
    InstituteRule(
        ruleNumber = 3,
        title = "Hardware Biometric Attendance Mandatory",
        description = "Daily physical attendance requires hardware-attested facial mesh verification at the campus turnstile gate. Turnstile logs are recorded within 3 seconds of entry/exit.",
        ruleCategory = "ATTENDANCE"
    ),
    InstituteRule(
        ruleNumber = 4,
        title = "Zero-Tolerance Academic Integrity & Exam Security",
        description = "All 100-question certification exams are proctored under Android Lock-Task Kiosk pinning with live liveness verification. Answers are committed using homomorphic cryptographic hashes. Any attempt to minimize the app, switch tasks, or use secondary devices results in immediate zero-score auto-submission.",
        ruleCategory = "EXAMINATION"
    ),
    InstituteRule(
        ruleNumber = 5,
        title = "Device Cryptographic Attestation & DPoP Binding",
        description = "Access to the institutional platform requires RFC 9449 Demonstrating Proof-of-Possession (DPoP) bound to the device's hardware Secure Element / StrongBox. Emulators, rooted environments, and modified APKs are permanently denied access.",
        ruleCategory = "SECURITY"
    ),
    InstituteRule(
        ruleNumber = 6,
        title = "Fine-Grained Zanzibar Zero-Trust Data Isolation",
        description = "All student records, performance metrics, and financial receipts are isolated via Google Zanzibar FGAC. Cross-student or cross-family data access is mathematically impossible under our zero-trust schema.",
        ruleCategory = "PRIVACY"
    ),
    InstituteRule(
        ruleNumber = 7,
        title = "Laboratory Equipment & Computing Environment Responsibility",
        description = "Students allocated computer lab terminals are strictly accountable for workstation hardware and software configuration. Running unauthorized binary executables or modifying network settings is strictly prohibited.",
        ruleCategory = "LABS"
    ),
    InstituteRule(
        ruleNumber = 8,
        title = "Punctuality & Automated T+20 Absence Notification",
        description = "Batch entry closes exactly at the designated start time. Unexplained absence beyond T+20 minutes automatically triggers instant security telemetry alerts to registered guardian mobile numbers.",
        ruleCategory = "DISCIPLINE"
    ),
    InstituteRule(
        ruleNumber = 9,
        title = "Professional Decorum & Mutual Respect Charter",
        description = "Students, faculty, and guardians must maintain institutional decorum. Bullying, harassment, or non-compliance with faculty lab instructions warrants immediate disciplinary committee review.",
        ruleCategory = "CONDUCT"
    ),
    InstituteRule(
        ruleNumber = 10,
        title = "Certification Issuance & Verification Standard",
        description = "Official Waqar Computer Institute diplomas and certificates are awarded solely upon achieving a minimum 75% biometric attendance rate and passing proctored exams with a minimum 50% score. All certificates feature verifiable Ed25519 QR signatures.",
        ruleCategory = "ACADEMICS"
    )
)

/**
 * Shared Institute 10-Rule Terms & Conditions Screen (F-190, F-310, F-430, F-520)
 * Pinned at the bottom of the Curved 3D Liquid Glass Sidebar across all four portals.
 */
@Composable
fun TermsAndConditionsScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassShaderTokens.DeepBackgroundVoid)
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = GlassShaderTokens.SpecularHighlight
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = "Charter",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Institute 10-Rule Charter",
                                color = GlassShaderTokens.SpecularHighlight,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Waqar Computer Institute • Main Campus, Unnao, UP",
                            color = GlassShaderTokens.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    GlassPill(
                        text = "ISO 9001:2015",
                        color = Color(0xFFA5B4FC)
                    )
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Institutional Governance Framework",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "These ten binding rules govern all students, faculty, administrators, and guardians across the unified institutional platform. Adherence is mandatory for campus admission, laboratory access, and certification.",
                            color = GlassShaderTokens.TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            itemsIndexed(WCI_TEN_RULES) { _, rule ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GlassPill(
                                    text = "Rule ${rule.ruleNumber}",
                                    color = Color(0xFF38BDF8)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = rule.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            GlassPill(
                                text = rule.ruleCategory,
                                color = GlassShaderTokens.TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = rule.description,
                            color = GlassShaderTokens.TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
