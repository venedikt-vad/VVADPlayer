package com.vvad.vp.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.compositionLocalOf

class AppHaptics(
    context: Context,
    private val enabled: Boolean,
    intensity: Int
) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val amplitude = when (intensity.coerceIn(1, 3)) {
        1 -> 70
        2 -> 150
        else -> 255
    }

    fun performLongPress() {
        vibrate(32L)
    }

    fun performTick() {
        vibrate(14L)
    }

    private fun vibrate(durationMs: Long) {
        if (!enabled) return
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(durationMs, amplitude)
        } else {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }
}

val LocalAppHaptics = compositionLocalOf<AppHaptics?> { null }
