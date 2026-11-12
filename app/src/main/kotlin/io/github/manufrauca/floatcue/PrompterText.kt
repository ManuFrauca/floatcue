package io.github.manufrauca.floatcue

import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.DisplayMetrics
import android.util.TypedValue

// Strip design tokens and text setup shared by the overlay, the settings preview and the duration estimate,
// so the three can never disagree about wrapping.
internal const val INK = 0xFF0E1014.toInt()
internal const val AMBER = 0xFFFFC107.toInt()
internal const val LINE_SPACING = 1.15f
internal const val PAD_LEFT_DP = 28f
internal const val PAD_RIGHT_DP = 12f
internal const val OVERLAY_MARGIN_DP = 16f

internal fun inkWithOpacity(percent: Int): Int = Color.argb(percent * 255 / 100, 0x0E, 0x10, 0x14)

internal fun spToPx(dm: DisplayMetrics, sp: Int): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp.toFloat(), dm)

internal fun lineHeightOf(paint: TextPaint): Float {
    val fm = paint.fontMetrics
    return (fm.descent - fm.ascent + fm.leading) * LINE_SPACING
}

/** The script with whole-word pause markers in bold amber. */
internal fun styledScript(script: String): SpannableString {
    val s = SpannableString(script)
    for (r in pauseRanges(script)) {
        s.setSpan(StyleSpan(Typeface.BOLD), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(AMBER), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return s
}

internal fun buildLayout(text: CharSequence, paint: TextPaint, widthPx: Int): StaticLayout =
    StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, LINE_SPACING)
        .build()

/** Number of lines the script wraps to on the real overlay (portrait, full width minus margin and paddings). */
internal fun countScriptLines(dm: DisplayMetrics, script: String, sizeSp: Int): Int {
    val w = (dm.widthPixels - (OVERLAY_MARGIN_DP + PAD_LEFT_DP + PAD_RIGHT_DP) * dm.density).toInt()
    if (w <= 0) return 0
    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = spToPx(dm, sizeSp) }
    return buildLayout(styledScript(script), paint, w).lineCount
}
