package com.waqar.institute.ui.student

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.waqar.institute.ai.AiTutorViewModel
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
 *
 * 5-Stage Zero-Bill AI Study Tutor Cascade:
 * L1 RAM -> L2 SQLCipher -> L3 Cloud Cache -> Grok API (Primary) -> Gemini Flash (Failover)
 *
 * Security & Zero-Billing Enforcements:
 * - Automatic blackout lock during active live examinations (Rule S-18).
 * - Live 24-Hour Intrusion & Prompt-Injection Lockout with 1-second countdown ticker.
 * - Client-side anti-spam debounce and local cooldown pacer (prevents button spamming).
 * - Real-time Server-Sent Events (SSE) progressive token streaming.
 */
@Composable
fun ZeroBillAiTutorScreen(
    isExamActive: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: AiTutorViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var queryInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new message / streaming chunk
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.text) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
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
                contentPadding = 16.dp
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
                                text = "L1 RAM → L2 SQLCipher → L3 Cloud Cache → Grok/Gemini",
                                color = GlassShaderTokens.BrandCyan,
                                fontSize = 10.sp
                            )
                        }
                    }

                    GlassPill(
                        text = if (uiState.isLocked) "SECURITY LOCKED" else "FREE TIER CASCADE",
                        color = if (uiState.isLocked) GlassShaderTokens.BrandRuby else GlassShaderTokens.BrandEmerald
                    )
                }
            }

            // Live 24-Hour Security Lockout Banner with Real-Time Countdown Ticker
            AnimatedVisibility(
                visible = uiState.isLocked,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = GlassShaderTokens.BrandRuby.copy(alpha = 0.2f),
                        contentPadding = 14.dp
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Security Lock",
                                    tint = GlassShaderTokens.BrandRuby,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "SECURITY LOCKOUT (24 HOURS ACTIVE)",
                                    color = GlassShaderTokens.BrandRuby,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Intrusion, reverse-engineering, or system prompt extraction attempt detected. Your AI Tutor has been automatically locked for 24 hours and high-priority incident telemetry logged to Admin.",
                                color = Color(0xEEFFFFFF),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Countdown",
                                    tint = GlassShaderTokens.BrandAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Unlock Countdown: ${uiState.lockoutCountdownFormatted.ifBlank { "23:59:59" }}",
                                    color = GlassShaderTokens.BrandAmber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conversation Messages Feed
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.messages) { msg ->
                    val isUser = msg.sender == "Student"
                    val isSecurityAlert = msg.sender.contains("Security", ignoreCase = true) || msg.sender.contains("Firewall", ignoreCase = true)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(if (isSecurityAlert) 0.95f else 0.85f),
                            backgroundColor = when {
                                isSecurityAlert -> GlassShaderTokens.BrandRuby.copy(alpha = 0.25f)
                                isUser -> GlassShaderTokens.BrandAccentBlue.copy(alpha = 0.25f)
                                else -> GlassShaderTokens.BrandMidnight
                            },
                            contentPadding = 12.dp
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = msg.sender,
                                        color = when {
                                            isSecurityAlert -> GlassShaderTokens.BrandRuby
                                            isUser -> GlassShaderTokens.BrandCyan
                                            else -> GlassShaderTokens.BrandPurple
                                        },
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

                if (uiState.isStreaming) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = GlassShaderTokens.BrandCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Streaming progressive study completion...",
                                color = GlassShaderTokens.BrandCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Anti-Spam Local Cooldown Indicator
            if (uiState.localCooldownSeconds > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Cooldown",
                        tint = GlassShaderTokens.BrandCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Anti-Spam Pacer: wait ${uiState.localCooldownSeconds}s before next query (prevents quota burn)",
                        color = GlassShaderTokens.BrandCyan,
                        fontSize = 11.sp
                    )
                }
            }

            // Input Dock
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassTextField(
                    value = queryInput,
                    onValueChange = {
                        if (!uiState.isLocked) {
                            queryInput = it
                        }
                    },
                    placeholder = if (uiState.isLocked) {
                        "Locked due to 24h security cage policy..."
                    } else if (uiState.localCooldownSeconds > 0) {
                        "Pacing query (${uiState.localCooldownSeconds}s cooldown)..."
                    } else {
                        "Ask doubt (e.g. How to pass journal entry for GST?)..."
                    },
                    modifier = Modifier.weight(1f)
                )

                GlassButton(
                    text = if (uiState.localCooldownSeconds > 0) "${uiState.localCooldownSeconds}s" else "Ask",
                    onClick = {
                        if (queryInput.isNotBlank() && uiState.canSend && !uiState.isLocked) {
                            val promptToSend = queryInput
                            queryInput = ""
                            viewModel.sendQuery(promptToSend)
                        }
                    },
                    variant = if (uiState.isLocked) GlassButtonVariant.OUTLINE else GlassButtonVariant.PRIMARY,
                    icon = {
                        Icon(
                            imageVector = if (uiState.isLocked) Icons.Default.Lock else Icons.Default.Send,
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
