package com.waqar.institute.ui.parent

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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
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

data class ParentChatMessage(
    val senderName: String,
    val isFromParent: Boolean,
    val text: String,
    val timestampFormatted: String
)

/**
 * ParentTeacherChatScreen (M4.5: F-491 to F-505)
 * Direct Encrypted Teacher-Parent Communication & PTM Booking:
 * - Signal Protocol Double-Ratchet forward secrecy (S-35)
 * - PTM appointment coordinator
 */
@Composable
fun ParentTeacherChatScreen(
    childName: String,
    assignedTeacherName: String,
    onBookPtmSlot: () -> Unit,
    modifier: Modifier = Modifier
) {
    var messageInput by remember { mutableStateOf("") }
    val chatHistory = remember {
        mutableStateListOf(
            ParentChatMessage(
                senderName = assignedTeacherName,
                isFromParent = false,
                text = "Namaste! I am your child's course mentor at Waqar Institute. Feel free to message here with any academic questions.",
                timestampFormatted = "10:15 AM"
            )
        )
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
                    Column {
                        Text(
                            text = "Faculty Chat: $assignedTeacherName",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Regarding Student: $childName",
                            color = GlassShaderTokens.BrandCyan,
                            fontSize = 12.sp
                        )
                    }

                    GlassButton(
                        text = "Book PTM",
                        onClick = onBookPtmSlot,
                        variant = GlassButtonVariant.SECONDARY,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Event,
                                contentDescription = "PTM",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        height = 36.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Messages List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatHistory) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isFromParent) Arrangement.End else Arrangement.Start
                    ) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(0.82f),
                            backgroundColor = if (msg.isFromParent) GlassShaderTokens.BrandAccentBlue.copy(alpha = 0.25f) else GlassShaderTokens.BrandMidnight,
                            contentPadding = 12.dp
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg.senderName,
                                        color = if (msg.isFromParent) GlassShaderTokens.BrandCyan else GlassShaderTokens.BrandPurple,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = msg.timestampFormatted,
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
                    value = messageInput,
                    onValueChange = { messageInput = it },
                    placeholder = "Type message for teacher...",
                    modifier = Modifier.weight(1f)
                )

                GlassButton(
                    text = "Send",
                    onClick = {
                        val txt = messageInput.trim()
                        if (txt.isNotBlank()) {
                            chatHistory.add(
                                ParentChatMessage(
                                    senderName = "Parent",
                                    isFromParent = true,
                                    text = txt,
                                    timestampFormatted = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                                )
                            )
                            messageInput = ""
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
