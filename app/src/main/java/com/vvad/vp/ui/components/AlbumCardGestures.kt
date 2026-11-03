package com.vvad.vp.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun Modifier.albumCardGestures(
    albumId: String,
    onOpen: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
): Modifier {
    val haptics = LocalAppHaptics.current
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)
    val currentOnAddToQueue by rememberUpdatedState(onAddToQueue)

    return pointerInput(albumId) {
        detectTapGestures(
            onTap = { currentOnOpen() },
            onDoubleTap = {
                haptics?.performLongPress()
                currentOnPlayNext()
            },
            onLongPress = {
                haptics?.performLongPress()
                currentOnAddToQueue()
            }
        )
    }
}
