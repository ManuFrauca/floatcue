package io.github.manufrauca.floatcue

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

internal const val SPEED_MIN = 10
internal const val SPEED_MAX = 80
internal const val SIZE_MIN = 18
internal const val SIZE_MAX = 56
internal const val OPACITY_MIN = 50
internal const val OPACITY_MAX = 100
internal const val HEIGHT_MIN = 20
internal const val HEIGHT_MAX = 60

/** Scroll speed for a reading pace of [lpm] lines per minute. */
internal fun pxPerSecond(lpm: Int, lineHeightPx: Float): Float = lpm * lineHeightPx / 60f

/** One Slower (0.9) / Faster (1.1) step: scaled, rounded, clamped to the valid range. */
internal fun stepSpeed(lpm: Int, factor: Float): Int = (lpm * factor).roundToInt().coerceIn(SPEED_MIN, SPEED_MAX)

/** Reading progress as scrollY / maxScroll, in 0..1 (0 when there is nothing to scroll). */
internal fun progressOf(scrollY: Float, maxScroll: Float): Float =
    if (maxScroll <= 0f) 0f else (scrollY / maxScroll).coerceIn(0f, 1f)

internal fun scrollFor(progress: Float, maxScroll: Float): Float = progress.coerceIn(0f, 1f) * maxScroll

/** Keeps a window of height [h] inside [top]..[screenH]. */
internal fun clampY(y: Int, top: Int, screenH: Int, h: Int): Int = y.coerceIn(top, max(top, screenH - h))

/** Exponential smoothing of [v] toward [target]; [tau] is the time constant in seconds. */
internal fun easeToward(v: Float, target: Float, dt: Float, tau: Float): Float =
    if (tau <= 0f) target else v + (target - v) * (1f - exp(-dt / tau))

internal fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotEmpty() }

/** Reading time for [lineCount] lines at [lpm]: the first line is already on the reading line. */
internal fun durationSeconds(lineCount: Int, lpm: Int): Float = max(0, lineCount - 1) * 60f / lpm

/** "about 1 min 20 s", "about 40 s", "about 2 min"; rounded to 5 s. */
internal fun durationLabel(seconds: Float): String {
    val s = ((seconds / 5f).roundToInt() * 5)
    if (s < 5) return "less than 5 s"
    val m = s / 60
    val r = s % 60
    return "about " + when {
        m == 0 -> "$r s"
        r == 0 -> "$m min"
        else -> "$m min $r s"
    }
}
