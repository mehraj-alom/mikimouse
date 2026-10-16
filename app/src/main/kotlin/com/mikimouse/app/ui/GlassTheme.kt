package com.mikimouse.app.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

private val Abyss = Color(0xFF03121D)
private val Accent = Color(0xFF7DD3FC)
private val Accent2 = Color(0xFF5EEAD4)

val OperatorDark = darkColorScheme(
    primary = Accent,
    secondary = Accent2,
    background = Abyss,
    surface = Color(0x14FFFFFF),
)

@Composable
fun GlassBackground() {
    Box(Modifier.fillMaxSize().background(Abyss)) {
        val blurMod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier.graphicsLayer {
                renderEffect = RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP).asComposeRenderEffect()
            }
        } else Modifier
        // Aqua light field.
        Box(blurMod.size(300.dp).offset((-70).dp, (-50).dp).clip(CircleShape).background(Color(0xFF0891B2).copy(alpha = 0.6f)))
        Box(blurMod.size(320.dp).offset(170.dp, 220.dp).clip(CircleShape).background(Color(0xFF0EA5E9).copy(alpha = 0.5f)))
        Box(blurMod.size(240.dp).offset(30.dp, 560.dp).clip(CircleShape).background(Color(0xFF2DD4BF).copy(alpha = 0.42f)))
        // Resting droplets (rings).
        DropletRing(Modifier.offset(300.dp, 120.dp).size(54.dp))
        DropletRing(Modifier.offset(40.dp, 380.dp).size(34.dp))
        DropletRing(Modifier.offset(250.dp, 640.dp).size(70.dp))
    }
}

@Composable
private fun DropletRing(mod: Modifier) {
    Box(
        mod.clip(CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            .background(
                Brush.radialGradient(
                    0.0f to Color.White.copy(alpha = 0.18f),
                    0.6f to Color.White.copy(alpha = 0.04f),
                    1.0f to Color.Transparent,
                ),
            ),
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                // Water body: bright top, deep bottom.
                Brush.linearGradient(
                    listOf(
                        Color(0xFFBAE6FD).copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.07f),
                        Color(0xFF0C4A6E).copy(alpha = 0.18f),
                    ),
                ),
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.3f),
                    ),
                ),
                shape,
            )
            .padding(16.dp),
    ) {
        // Specular droplet gloss, top edge.
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.05f),
                        Color.Transparent,
                    ),
                ),
            ),
        )
        content()
    }
}
