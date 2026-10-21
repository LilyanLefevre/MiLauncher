package com.lilyan_lefevre.milauncher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
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
    TileFrame(label = app.label, showLabel = true, onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
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
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(if (focused && showLabel) 1f else 0f, label = "labelAlpha")

    // Vertical padding leaves room for the zoom and ring: scrolling only sees the unscaled bounds.
    Column(modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
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
            content()
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
