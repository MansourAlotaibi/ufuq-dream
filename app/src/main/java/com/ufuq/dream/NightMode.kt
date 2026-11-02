package com.ufuq.dream

import android.graphics.Color
import java.util.Calendar
import java.util.Date

/**
 * Gradual night dimming for a living-room TV.
 * Fully dim from 23:30 until 05:00; ramps in from 22:00 and out until 06:30, so the change is never noticed as a step.
 */
object NightMode {
    /** Maximum black veil drawn over the artwork. */
    const val BACKGROUND_DIM = 0.42f
    /** Maximum reduction of text opacity. */
    const val TEXT_DIM = 0.22f

    private const val RAMP_MINUTES = 90
    private const val RAMP_START = 22 * 60
    private const val DARK_UNTIL = 5 * 60

    /** 0 = daytime/evening, 1 = deepest night. */
    fun level(now: Date): Float {
        val calendar = Calendar.getInstance().apply { time = now }
        val minute = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return when {
            minute >= RAMP_START -> ((minute - RAMP_START).toFloat() / RAMP_MINUTES).coerceAtMost(1f)
            minute < DARK_UNTIL -> 1f
            minute < DARK_UNTIL + RAMP_MINUTES -> 1f - (minute - DARK_UNTIL).toFloat() / RAMP_MINUTES
            else -> 0f
        }
    }

    fun backgroundDim(now: Date, enabled: Boolean): Float = if (enabled) level(now) * BACKGROUND_DIM else 0f

    fun textFactor(now: Date, enabled: Boolean): Float = if (enabled) 1f - level(now) * TEXT_DIM else 1f

    /** Scales the opacity of [color] by [factor] (1 = unchanged). */
    fun fade(color: Int, factor: Float): Int {
        if (factor >= 1f) return color
        return Color.argb((Color.alpha(color) * factor).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }
}
