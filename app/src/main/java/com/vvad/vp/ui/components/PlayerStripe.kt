package com.vvad.vp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun PlayerStripe() {
    var offsetX by remember { mutableStateOf(0f) }

    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (offsetX > 100) onPreviousTrack() else if (offsetX < -100) onNextTrack()
                    offsetX = 0f
                },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount
                }
            )
        }
    ) {
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(Color.Gray), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.White)
            }
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Song Title", style = MaterialTheme.typography.bodyLarge)
                Text("Artist Name", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { /* Play/Pause */ }) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(32.dp))
            }
        }
    }
}

fun onNextTrack() = println("Next Track")
fun onPreviousTrack() = println("Previous Track")