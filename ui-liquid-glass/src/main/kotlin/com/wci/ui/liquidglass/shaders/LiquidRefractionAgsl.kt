package com.wci.ui.liquidglass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.wci.ui.liquidglass.theme.GlassShaderTokens

/**
 * LiquidRefractionAgsl
 * Android AGSL RuntimeShader implementing 3D Ultra Liquid Glass caustics,
 * curvature refraction, chromatic dispersion, and dynamic specular highlights.
 */
object LiquidRefractionAgsl {

    private const val AGSL_LIQUID_SRC = """
        uniform shader composableImage;
        uniform float2 size;
        uniform float time;
        uniform float refractionIndex; // e.g. 1.08

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / size;
            float2 center = float2(0.5, 0.5);
            float2 delta = uv - center;
            float dist = length(delta);

            // Dynamic fluid wave perturbation
            float wave1 = sin(uv.x * 8.0 + time * 1.5) * 0.015;
            float wave2 = cos(uv.y * 8.0 - time * 1.2) * 0.015;
            float2 offset = delta * (dist * (refractionIndex - 1.0)) + float2(wave1, wave2);

            // Chromatic dispersion (RGB offset sampling)
            float2 uvR = uv - offset * 1.02;
            float2 uvG = uv - offset;
            float2 uvB = uv - offset * 0.98;

            half4 colorR = composableImage.eval(uvR * size);
            half4 colorG = composableImage.eval(uvG * size);
            half4 colorB = composableImage.eval(uvB * size);

            half4 finalColor = half4(colorR.r, colorG.g, colorB.b, colorG.a);

            // Specular caustic gloss
            float light = pow(max(0.0, 1.0 - dist * 1.8), 3.0) * 0.35;
            finalColor.rgb += half3(light, light * 1.1, light * 1.2);

            return finalColor;
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createShader(): RuntimeShader {
        return RuntimeShader(AGSL_LIQUID_SRC)
    }
}

/**
 * Modifier extension applying real-time liquid glass refraction.
 */
@Composable
fun Modifier.liquidGlassEffect(
    refractionIndex: Float = 1.08f,
    enabled: Boolean = true
): Modifier {
    if (!enabled) return this

    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass_transition")
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f, // 2*PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time_uniform"
    )

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { LiquidRefractionAgsl.createShader() }
        this.graphicsLayer {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("time", animTime)
            shader.setFloatUniform("refractionIndex", refractionIndex)
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "composableImage").asComposeRenderEffect()
        }
    } else {
        // Fallback for Android 7.0 - 12 (API 24-32)
        this.drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x33FFFFFF),
                        Color(0x0AFFFFFF),
                        Color(0x1A00D4B2)
                    )
                )
            )
        }
    }
}
