package com.vvad.vp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.vvad.vp.data.PlaybackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    playbackManager: PlaybackManager,
    onBack: () -> Unit,
    bottomContentPadding: Dp = 0.dp
) {
    val queue = playbackManager.queue
    val currentIndex = playbackManager.currentQueueIndex
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 72.dp.toPx() }
    val fallbackRowHeightPx = with(density) { 65.dp.toPx() }

    var draggedQueueId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var autoScrollDirection by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    fun stopAutoScroll() {
        autoScrollDirection = 0f
        autoScrollJob?.cancel()
        autoScrollJob = null
    }

    fun applyDragReorder(queueId: String, itemHeight: Float) {
        if (itemHeight <= 0f) return
        var index = playbackManager.queue.indexOfFirst { it.queueId == queueId }
        if (index == -1) return

        while (dragOffsetY > itemHeight / 2f && index < playbackManager.queue.lastIndex) {
            if (!playbackManager.moveQueueItem(index, index + 1)) break
            dragOffsetY -= itemHeight
            index++
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        while (dragOffsetY < -itemHeight / 2f && index > 0) {
            if (!playbackManager.moveQueueItem(index, index - 1)) break
            dragOffsetY += itemHeight
            index--
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun updateAutoScroll(centerY: Float) {
        val layoutInfo = listState.layoutInfo
        val direction = when {
            centerY <= layoutInfo.viewportStartOffset + edgeThresholdPx -> -1f
            centerY >= layoutInfo.viewportEndOffset - edgeThresholdPx -> 1f
            else -> 0f
        }
        autoScrollDirection = direction

        if (direction == 0f) {
            autoScrollJob?.cancel()
            autoScrollJob = null
        } else if (autoScrollJob == null) {
            autoScrollJob = scope.launch {
                while (isActive) {
                    val activeDirection = autoScrollDirection
                    if (activeDirection == 0f) break
                    val draggedId = draggedQueueId ?: break
                    val itemHeight = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == draggedId }
                        ?.size
                        ?.toFloat()
                        ?: fallbackRowHeightPx
                    val consumed = listState.scrollBy(activeDirection * 12f)
                    dragOffsetY += consumed
                    applyDragReorder(draggedId, itemHeight)
                    delay(16)
                }
            }
        }
    }

    fun finishDrag() {
        draggedQueueId = null
        dragOffsetY = 0f
        stopAutoScroll()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (queue.size > 1) {
                        IconButton(onClick = { playbackManager.clearQueueExceptCurrent() }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear queue",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Queue is empty", color = Color.White.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(bottom = bottomContentPadding)
            ) {
                itemsIndexed(
                    items = queue,
                    key = { _, entry -> entry.queueId }
                ) { index, entry ->
                    val isCurrent = index == currentIndex
                    val isDragging = draggedQueueId == entry.queueId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                val scale = if (isDragging) 1.01f else 1f
                                scaleX = scale
                                scaleY = scale
                            }
                            .clickable { playbackManager.seekToQueueIndex(index) }
                            .background(
                                when {
                                    isDragging -> Color.White.copy(alpha = 0.16f)
                                    isCurrent -> Color.White.copy(alpha = 0.1f)
                                    else -> Color.Black
                                }
                            )
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = entry.coverArtUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.track.title,
                                color = if (isCurrent) Color(0xFFE53935) else Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1
                            )
                            Text(
                                text = entry.track.artists.joinToString(", ") { it.name },
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = entry.albumName,
                            color = Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.widthIn(max = 96.dp)
                        )
                        if (!isCurrent) {
                            IconButton(
                                onClick = { playbackManager.removeQueueItem(entry.queueId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove from queue",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .pointerInput(entry.queueId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedQueueId = entry.queueId
                                            dragOffsetY = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y

                                            val currentInfo = listState.layoutInfo.visibleItemsInfo
                                                .firstOrNull { it.key == entry.queueId }
                                            val itemHeight = currentInfo?.size?.toFloat() ?: fallbackRowHeightPx
                                            applyDragReorder(entry.queueId, itemHeight)

                                            currentInfo?.let { info ->
                                                val centerY = info.offset + info.size / 2f + dragOffsetY
                                                updateAutoScroll(centerY)
                                            }
                                        },
                                        onDragEnd = {
                                            finishDrag()
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragCancel = { finishDrag() }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Drag to reorder",
                                tint = if (isDragging) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (index < queue.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}
