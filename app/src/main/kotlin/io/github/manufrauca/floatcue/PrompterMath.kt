package io.github.manufrauca.floatcue

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
