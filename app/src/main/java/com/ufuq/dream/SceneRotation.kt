package com.ufuq.dream

/**
 * Counts visible monotonic time only. No wall-clock jumps, no idle/background loading.
 *
 * [startOffset] lets each dream session open on a different scene, so short sessions
 * (the usual case on a TV) still see all three scenes over time instead of always Diriyah.
 */
class SceneRotation(startOffset: Int = 0) {
    companion object { const val PERIOD = 15 * 60 * 1000L }

    private val offset = startOffset.coerceAtLeast(0)
    private var accumulated = 0L
    private var since: Long? = null

    fun resume(now: Long) { if (since == null) since = now }
    fun pause(now: Long) { accumulated = elapsed(now); since = null }
    fun elapsed(now: Long) = accumulated + (since?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun scene(now: Long) = (((elapsed(now) / PERIOD) + offset) % 3).toInt()
    fun remaining(now: Long) = PERIOD - elapsed(now) % PERIOD
}
