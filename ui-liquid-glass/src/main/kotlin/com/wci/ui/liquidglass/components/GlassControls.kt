package com.wci.ui.liquidglass.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wci.ui.liquidglass.theme.GlassShaderTokens

enum class GlassButtonVariant {
    PRIMARY,
    SECONDARY,
    DANGER,
    ACCENT;

    companion object {
        val Secondary: GlassButtonVariant = SECONDARY
        val Primary: GlassButtonVariant = PRIMARY
        val Danger: GlassButtonVariant = DANGER
        val Accent: GlassButtonVariant = ACCENT
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GlassButtonVariant = GlassButtonVariant.PRIMARY,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: @Composable (() -> Unit)? = null,
    height: Dp = 50.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.97f else 1f, label = "btn_press_scale")

    val bgBrush = when (variant) {
        GlassButtonVariant.PRIMARY -> Brush.horizontalGradient(
            listOf(Color(0xFF0066FF), Color(0xFF00D4B2))
        )
        GlassButtonVariant.ACCENT -> GlassShaderTokens.ActiveGlowGradient
        GlassButtonVariant.DANGER -> Brush.horizontalGradient(
            listOf(Color(0xFFFF3366), Color(0xFFFF6699))
        )
        GlassButtonVariant.SECONDARY -> Brush.verticalGradient(
            listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF))
        )
    }

    val borderBrush = when (variant) {
        GlassButtonVariant.SECONDARY -> GlassShaderTokens.SpecularBorderGradient
        else -> Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x26FFFFFF)))
    }

    val shape = RoundedCornerShape(GlassShaderTokens.CornerRadiusMedium)

    Box(
        modifier = modifier
            .scale(scale)
            .height(height)
            .clip(shape)
            .background(if (enabled) bgBrush else Brush.linearGradient(listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF))))
            .border(1.dp, borderBrush, shape)
            .clickable(
                enabled = enabled && !isLoading,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp)
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = if (enabled) Color.White else Color(0x66FFFFFF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    enabled: Boolean = true
) {
    val shape = RoundedCornerShape(GlassShaderTokens.CornerRadiusMedium)
    val borderBrush = if (isError) {
        Brush.horizontalGradient(listOf(Color(0xFFFF3366), Color(0xFFFF6699)))
    } else {
        GlassShaderTokens.SpecularBorderGradient
    }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                color = Color(0xCCFFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(GlassShaderTokens.GlassTintDeep)
                .border(1.dp, borderBrush, shape)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0x66FFFFFF),
                            fontSize = 14.sp
                        )
                    }

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(GlassShaderTokens.BrandCyan),
                        visualTransformation = visualTransformation,
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions,
                        enabled = enabled,
                        singleLine = true
                    )
                }

                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    trailingIcon()
                }
            }
        }

        if (isError && !errorMessage.isNullOrEmpty()) {
            Text(
                text = errorMessage,
                color = Color(0xFFFF3366),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}

@Composable
fun GlassPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = GlassShaderTokens.BrandCyan,
    containerColor: Color? = null,
    contentColor: Color? = null,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(GlassShaderTokens.CornerRadiusPill)
    val textColor = contentColor ?: color
    val bgColor = containerColor ?: color.copy(alpha = 0.15f)
    val borderColor = containerColor?.copy(alpha = 0.40f) ?: color.copy(alpha = 0.40f)

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
