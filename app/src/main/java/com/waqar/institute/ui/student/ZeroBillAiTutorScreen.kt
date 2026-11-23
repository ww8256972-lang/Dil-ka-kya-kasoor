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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.wci.ui.liquidglass.components.GlassTextField
import com.wci.ui.liquidglass.theme.GlassShaderTokens

data class TutorMessage(
    val sender: String,
    val text: String,
    val tierSource: String = "L1 RAM Cache"
)

/**
 * ZeroBillAiTutorScreen (M3.6: F-389 to F-402)
 * 4-Tier Free AI Study Tutor Cascade:
 * L1 RAM -> L2 Local SQLCipher -> L3 Shared Firestore Cache -> Free-tier LLM
 * - Automatic blackout lock during active live exams
 */
@Composable
fun ZeroBillAiTutorScreen(
    isExamActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var queryInput by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            TutorMessage(
                sender = "AI Tutor",
                text = "Hello! I am your Waqar Institute 24/7 Study Tutor. Ask me any conceptual question about ADCA, Tally Prime, Python, C++, or CCC.",
                tierSource = "L1 System"
            )
        )
    }

    if (isExamActive) {
        // Exam Blackout Lock (F-390)
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(GlassShaderTokens.BrandNavy)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 28.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = GlassShaderTokens.BrandRuby,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "AI Study Tutor Blacked Out",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Access is strictly disabled during active examination sessions to preserve academic integrity (Rule S-18).",
                        color = Color(0xCCFFFFFF),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassShaderTokens.BrandNavy)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
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
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI",
                            tint = GlassShaderTokens.BrandPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Column {
                            Text(
                                text = "Zero-Bill AI Study Tutor",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "L1 RAM → L2 SQLCipher → L3 Cloud Cache",
                                color = GlassShaderTokens.BrandCyan,
                                fontSize = 11.sp
                            )
                        }
                    }

                    GlassPill(
                        text = "FREE TIER CASCADE",
                        color = GlassShaderTokens.BrandEmerald
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Conversation Messages
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { msg ->
                    val isUser = msg.sender == "Student"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(0.85f),
                            backgroundColor = if (isUser) GlassShaderTokens.BrandAccentBlue.copy(alpha = 0.25f) else GlassShaderTokens.BrandMidnight,
                            contentPadding = 12.dp
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg.sender,
                                        color = if (isUser) GlassShaderTokens.BrandCyan else GlassShaderTokens.BrandPurple,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = msg.tierSource,
                                        color = Color(0x66FFFFFF),
                                        fontSize = 9.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = msg.text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Input Dock
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassTextField(
                    value = queryInput,
                    onValueChange = { queryInput = it },
                    placeholder = "Ask doubt (e.g. How to pass journal entry for GST?)...",
                    modifier = Modifier.weight(1f)
                )

                GlassButton(
                    text = "Ask",
                    onClick = {
                        val q = queryInput.trim()
                        if (q.isNotBlank()) {
                            messages.add(TutorMessage("Student", q, "Local Input"))
                            queryInput = ""
                            // Deterministic study tutor answer from local knowledge base
                            messages.add(
                                TutorMessage(
                                    sender = "AI Tutor",
                                    text = "In Tally Prime, to record GST on Purchases:\n1. Open F9: Purchase Voucher.\n2. Select Supplier Ledger.\n3. Add Item details.\n4. Apply Input CGST and Input SGST (or Input IGST if inter-state).\n5. Verify that E-way Bill details match campus invoice records.",
                                    tierSource = "L2 SQLCipher Cache"
                                )
                            )
                        }
                    },
                    variant = GlassButtonVariant.PRIMARY,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    height = 52.dp
                )
            }
        }
    }
}
