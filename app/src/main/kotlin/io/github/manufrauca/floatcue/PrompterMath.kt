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

/**
 * Where the read sweep's edge is on a line spanning [top]..[bottom] (layout y) and [left]..[right] (x):
 * at [left] when scrollY reaches the line, one [edge] past [right] when it leaves, so it ends fully grey.
 */
internal fun sweepX(scrollY: Float, top: Float, bottom: Float, left: Float, right: Float, edge: Float): Float {
    val f = if (bottom <= top) 1f else ((scrollY - top) / (bottom - top)).coerceIn(0f, 1f)
    return left + f * (right - left + edge)
}

/** The text a share should load as the script, cut to [max] chars; null when it carries none (e.g. a file). */
internal fun sharedText(extra: CharSequence?, max: Int): String? = extra?.toString()?.take(max)?.takeIf { it.isNotBlank() }

internal fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotEmpty() }

/** Reading time for [lineCount] lines at [lpm]: every line, the last one included, is swept once. */
internal fun durationSeconds(lineCount: Int, lpm: Int): Float = max(0, lineCount) * 60f / lpm

/** Reading time rounded to 5 s, as whole minutes and remaining seconds; (0, 0) means under 5 s. */
internal fun roundedDuration(seconds: Float): Pair<Int, Int> {
    val s = (seconds / 5f).roundToInt() * 5
    return s / 60 to s % 60
}
