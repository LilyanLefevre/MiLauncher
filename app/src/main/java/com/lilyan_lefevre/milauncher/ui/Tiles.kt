package com.lilyan_lefevre.milauncher.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lilyan_lefevre.milauncher.data.InstalledApp

val TileSpacing = 16.dp

/** App tile: 16:9 banner, zoom and ring on focus, name shown only when focused. */
@Composable
fun AppTile(app: InstalledApp, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    TileFrame(
        label = app.label,
        showLabel = true,
        glowColor = app.accent?.let(::Color),
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
    ) {
        Image(
            bitmap = app.image,
            contentDescription = null,
            contentScale = if (app.hasBanner) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .then(if (app.hasBanner) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        )
    }
}

/** Text tile for launcher actions (manage apps, settings). */
@Composable
fun ActionTile(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TileFrame(label = label, showLabel = false, onClick = onClick, modifier = modifier) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TileFrame(
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
    glowColor: Color? = null,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused && showLabel) 1f else 0f, label = "labelAlpha")
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(durationMillis = 250), label = "glow")
    val color = glowColor ?: MaterialTheme.colorScheme.primary
    // Light band swept once across the banner each time the tile gains focus.
    val shine = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        if (focused) {
            shine.snapTo(0f)
            shine.animateTo(1f, tween(durationMillis = 550, delayMillis = 80, easing = FastOutSlowInEasing))
        }
    }

    // Vertical padding leaves room for the zoom and ring: scrolling only sees the unscaled bounds.
    Column(modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (glow > 0f) Glow(color, glow, Modifier.matchParentSize())
            Card(
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = Modifier
                    .matchParentSize()
                    .onFocusChanged { focused = it.isFocused },
                shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                scale = CardDefaults.scale(focusedScale = 1.1f),
                // Ring offset from the tile so it stays visible on light banners.
                border = CardDefaults.border(
                    focusedBorder = Border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.border),
                        inset = 4.dp,
                        shape = RoundedCornerShape(14.dp)
                    )
                )
            ) {
                Box(
                    Modifier.fillMaxSize().drawWithContent {
                        drawContent()
                        if (focused) drawShine(shine.value)
                    }
                ) {
                    content()
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Clears the zoomed tile and its ring, which reach about 7 dp below the unscaled bounds.
            modifier = Modifier.padding(top = 14.dp).alpha(labelAlpha)
        )
    }
}

/** Halo of [color] behind a focused tile, following its 1.1 zoom; [strength] fades it in and out. */
@Composable
private fun Glow(color: Color, strength: Float, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // GPU blur (RenderEffect, Android 12+); only the focused tile pays for it.
        Box(
            modifier
                .graphicsLayer {
                    alpha = strength
                    scaleX = 1f + 0.12f * strength
                    scaleY = 1f + 0.2f * strength
                }
                .blur(22.dp, BlurredEdgeTreatment.Unbounded)
                .background(color.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
        )
    } else {
        Box(modifier.drawBehind { drawLayeredGlow(color, strength) })
    }
}

/** Fallback without blur: stacked rounded rects fading outwards. */
private fun DrawScope.drawLayeredGlow(color: Color, strength: Float) {
    val layers = 6
    val spread = 28.dp.toPx() * strength
    val zoom = 1f + 0.1f * strength
    val width = size.width * zoom
    val height = size.height * zoom
    for (i in layers downTo 1) {
        val grow = spread * i / layers
        drawRoundRect(
            color = color.copy(alpha = 0.3f * strength * (1f - (i - 1f) / layers)),
            topLeft = Offset((size.width - width) / 2 - grow, (size.height - height) / 2 - grow),
            size = Size(width + 2 * grow, height + 2 * grow),
            cornerRadius = CornerRadius(14.dp.toPx() + grow)
        )
    }
}

/** Diagonal light band at [progress] (0 to 1) of its way across the tile. */
private fun DrawScope.drawShine(progress: Float) {
    if (progress <= 0f || progress >= 1f) return
    val band = size.width * 0.35f
    val x = -band + (size.width + 2 * band) * progress
    drawRect(
        Brush.linearGradient(
            0f to Color.Transparent,
            0.5f to Color.White.copy(alpha = 0.28f),
            1f to Color.Transparent,
            start = Offset(x - band, 0f),
            end = Offset(x + band, size.height)
        )
    )
}
