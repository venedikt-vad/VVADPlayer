package com.vvad.vp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

private val playlistGradients = listOf(
    listOf(Color(0xFF3E2723), Color(0xFF6D4C41)),
    listOf(Color(0xFF1A237E), Color(0xFF3949AB)),
    listOf(Color(0xFF004D40), Color(0xFF00897B)),
    listOf(Color(0xFF4A148C), Color(0xFF7B1FA2)),
    listOf(Color(0xFF880E4F), Color(0xFFAD1457)),
    listOf(Color(0xFF1B5E20), Color(0xFF388E3C)),
    listOf(Color(0xFF0D47A1), Color(0xFF1976D2)),
    listOf(Color(0xFFBF360C), Color(0xFFE64A19))
)

@Composable
fun PlaylistCover(
    coverArtUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    seed: String = contentDescription ?: coverArtUrl,
    centerText: String? = null
) {
    if (coverArtUrl.isNotBlank()) {
        AsyncImage(
            model = coverArtUrl,
            contentDescription = contentDescription,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        val hash = seed.hashCode()
        val colors = playlistGradients[(hash % playlistGradients.size + playlistGradients.size) % playlistGradients.size]

        Box(
            modifier = modifier
                .clip(shape)
                .background(Brush.linearGradient(colors)),
            contentAlignment = Alignment.Center
        ) {
            if (centerText != null) {
                Text(
                    text = centerText,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(10.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = contentDescription,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.fillMaxSize(0.4f)
                )
            }
        }
    }
}
