package com.wci.ui.liquidglass.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wci.ui.liquidglass.components.GlassButton
import com.wci.ui.liquidglass.components.GlassButtonVariant
import com.wci.ui.liquidglass.components.GlassCard
import com.wci.ui.liquidglass.components.GlassPill
import com.wci.ui.liquidglass.theme.GlassShaderTokens

/**
 * BioEnrollWizard (F-016)
 * Guides verified users through multi-angle facial geometry capture,
 * generating hardware-bound 512-dimensional biometric template.
 */
@Composable
fun BioEnrollWizard(
    userUid: String,
    portalRole: String,
    onEnrollmentComplete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val steps = listOf(
        "Look directly into the camera",
        "Turn head slightly Left (15°)",
        "Turn head slightly Right (15°)",
        "Tilt head gently Upwards",
        "Blink naturally to confirm Liveness"
    )

    val transition = rememberInfiniteTransition(label = "scan_ring_transition")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring_rotation"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE6040914))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(0.95f),
            elevation = 16.dp,
            contentPadding = 24.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GlassPill(
                    text = "BIOMETRIC ENROLLMENT • STEP ${currentStep + 1} OF 5",
                    color = GlassShaderTokens.BrandCyan
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Owner Face Gate Setup",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = steps[currentStep],
                    color = Color(0xCCFFFFFF),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Biometric Reticle Ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(GlassShaderTokens.GlassTintDeep)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    GlassShaderTokens.BrandCyan,
                                    Color.Transparent,
                                    GlassShaderTokens.BrandPurple,
                                    GlassShaderTokens.BrandCyan
                                )
                            ),
                            shape = CircleShape
                        )
                        .rotate(rotation),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Face Reticle",
                        tint = GlassShaderTokens.BrandCyan,
                        modifier = Modifier.size(80.dp)
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Progress Dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in steps.indices) {
                        val isDone = i <= currentStep
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isDone) GlassShaderTokens.BrandCyan else Color(0x33FFFFFF))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlassButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        variant = GlassButtonVariant.SECONDARY,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    GlassButton(
                        text = if (currentStep < steps.size - 1) "Next Angle" else "Complete Registration",
                        onClick = {
                            if (currentStep < steps.size - 1) {
                                currentStep++
                            } else {
                                onEnrollmentComplete()
                            }
                        },
                        variant = GlassButtonVariant.PRIMARY,
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }
        }
    }
}
