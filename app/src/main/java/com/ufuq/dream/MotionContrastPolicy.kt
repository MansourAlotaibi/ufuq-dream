package com.ufuq.dream

/** Preserve city detail: video has no full-width photographic contrast veil.
 * A separate soft veil follows the live text group. Keep the user's night opt-in,
 * but cap its artwork dimming on dark footage instead of crushing near-black detail.
 */
internal object MotionContrastPolicy {
    fun nightDim(requested:Float,posterLuminance:Float?):Float {
        val luminance=(posterLuminance?:.3f).coerceIn(0f,1f)
        val cap=when {luminance<.24f->.06f;luminance>.65f->.16f;else->.10f}
        return requested.coerceIn(0f,cap)
    }
}
