package com.wci.ui.liquidglass.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GlassShaderTokens (Section 2 & PRD UI Matrix)
 * WCAG AAA compliant 3D Ultra Liquid Glass and Frosted Glassmorphism visual tokens.
 */
object GlassShaderTokens {

    // Core Brand Palette (Waqar Computer Institute)
    val BrandNavy = Color(0xFF040914)
    val BrandMidnight = Color(0xFF081226)
    val BrandSurfaceDark = Color(0xFF0D1B36)
    val BrandAccentBlue = Color(0xFF0066FF)
    val BrandCyan = Color(0xFF00D4B2)
    val BrandPurple = Color(0xFF635BFF)
    val BrandEmerald = Color(0xFF00E599)
    val BrandRuby = Color(0xFFFF3366)
    val BrandAmber = Color(0xFFFFB800)

    // Glass Translucency Tints
    val GlassTintSubtle = Color(0x14FFFFFF) // 8% opacity
    val GlassTintNormal = Color(0x26FFFFFF) // 15% opacity
    val GlassTintProminent = Color(0x40FFFFFF) // 25% opacity
    val GlassTintDeep = Color(0x590D1B36) // 35% dark tint

    // Glass Border Colors
    val GlassBorderLight = Color(0x4DFFFFFF) // 30% white
    val GlassBorderSubtle = Color(0x26FFFFFF) // 15% white
    val GlassBorderActive = Color(0x8000D4B2) // Cyan glow

    // Glass Specular Gradients
    val SpecularGradientTop = Brush.verticalGradient(
        colors = listOf(
            Color(0x66FFFFFF),
            Color(0x1AFFFFFF),
            Color(0x05FFFFFF)
        )
    )

    val SpecularBorderGradient = Brush.linearGradient(
        colors = listOf(
            Color(0x99FFFFFF),
            Color(0x26FFFFFF),
            Color(0x0DFFFFFF),
            Color(0x66FFFFFF)
        )
    )

    val ActiveGlowGradient = Brush.horizontalGradient(
        colors = listOf(
            BrandCyan,
            BrandAccentBlue,
            BrandPurple
        )
    )

    // Blur Radii
    val BlurRadiusSubtle: Dp = 8.dp
    val BlurRadiusMedium: Dp = 16.dp
    val BlurRadiusDeep: Dp = 28.dp
    val BlurRadiusLiquid: Dp = 36.dp

    // Corner Radii
    val CornerRadiusSmall: Dp = 10.dp
    val CornerRadiusMedium: Dp = 16.dp
    val CornerRadiusLarge: Dp = 24.dp
    val CornerRadiusPill: Dp = 999.dp

    // Noise Texture Alpha
    const val GlassNoiseAlpha = 0.035f
}
