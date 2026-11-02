package com.vvad.vp.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
fun Modifier.albumCardGestures(
    albumId: String,
    onOpen: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)
    val currentOnAddToQueue by rememberUpdatedState(onAddToQueue)

    return pointerInput(albumId) {
        detectTapGestures(
            onTap = { currentOnOpen() },
            onDoubleTap = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnPlayNext()
            },
            onLongPress = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnAddToQueue()
            }
        )
    }
}
