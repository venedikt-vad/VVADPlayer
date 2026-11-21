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

    private val level = intensity.coerceIn(1, 3)
    private val amplitude = when (level) {
        1 -> 150
        2 -> 205
        else -> 255
    }
    private val longPressDurationMs = when (level) {
        1 -> 35L
        2 -> 55L
        else -> 70L
    }
    private val tickDurationMs = when (level) {
        1 -> 12L
        2 -> 18L
        else -> 25L
    }

    fun performLongPress() {
        vibrate(longPressDurationMs, amplitude, strongDoublePulse = level == 3)
    }

    fun performTick() {
        vibrate(tickDurationMs, amplitude)
    }

    private fun vibrate(
        durationMs: Long,
        amplitude: Int,
        strongDoublePulse: Boolean = false
    ) {
        if (!enabled) return
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return

        val effect = if (vibrator.hasAmplitudeControl()) {
            if (strongDoublePulse) {
                VibrationEffect.createWaveform(
                    longArrayOf(0L, durationMs, 24L, durationMs),
                    intArrayOf(0, amplitude, 0, amplitude),
                    -1
                )
            } else {
                VibrationEffect.createOneShot(durationMs, amplitude)
            }
        } else {
            if (strongDoublePulse) {
                VibrationEffect.createWaveform(
                    longArrayOf(0L, durationMs, 24L, durationMs),
                    -1
                )
            } else {
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
        }
        vibrator.vibrate(effect)
    }
}

val LocalAppHaptics = compositionLocalOf<AppHaptics?> { null }
