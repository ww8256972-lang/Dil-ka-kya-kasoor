package com.wci.ui.liquidglass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wci.ui.liquidglass.theme.GlassShaderTokens

/**
 * GlassSurface
 * Foundational container applying frosted glassmorphism translucency,
 * specular borders, and elevation shadow.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassShaderTokens.CornerRadiusMedium),
    backgroundColor: Color = GlassShaderTokens.GlassTintNormal,
    borderBrush: Brush = GlassShaderTokens.SpecularBorderGradient,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 4.dp,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val effectiveBg = containerColor ?: backgroundColor
    Box(
        modifier = modifier
            .shadow(elevation = elevation, shape = shape, clip = false)
            .clip(shape)
            .background(effectiveBg)
            .border(width = borderWidth, brush = borderBrush, shape = shape),
        content = content
    )
}

/**
 * GlassCard
 * Interactive frosted glass card with padding and optional click handler.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(GlassShaderTokens.CornerRadiusMedium),
    backgroundColor: Color = GlassShaderTokens.GlassTintNormal,
    borderBrush: Brush = GlassShaderTokens.SpecularBorderGradient,
    borderWidth: Dp = 1.dp,
    elevation: Dp = 6.dp,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    GlassSurface(
        modifier = modifier.then(clickableModifier),
        shape = shape,
        backgroundColor = containerColor ?: backgroundColor,
        borderBrush = borderBrush,
        borderWidth = borderWidth,
        elevation = elevation,
        containerColor = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
