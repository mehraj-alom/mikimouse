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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.dp

private val Abyss = Color(0xFF070B16)
private val Accent = Color(0xFF7DD3FC)
private val Accent2 = Color(0xFFA78BFA)

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
        Box(blurMod.size(260.dp).offset((-60).dp, (-40).dp).clip(CircleShape).background(Color(0xFF0EA5E9).copy(alpha = 0.55f)))
        Box(blurMod.size(300.dp).offset(180.dp, 220.dp).clip(CircleShape).background(Color(0xFF8B5CF6).copy(alpha = 0.5f)))
        Box(blurMod.size(220.dp).offset(40.dp, 560.dp).clip(CircleShape).background(Color(0xFFEC4899).copy(alpha = 0.4f)))
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .padding(16.dp),
    ) { content() }
}
