package io.github.manufrauca.floatcue

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.withTranslation
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.max

/** Scrolling text view. Time-based scrolling via Choreographer; tap toggles, vertical drag scrubs. */
class PrompterView(context: Context) : View(context) {
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(2.5f * density, 0f, 1f * density, 0xCC000000.toInt())
    }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val padLeft = 28 * density
    private val padRight = 12 * density
    private val fade = 16 * density
    private val caretSize = 10 * density
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val caretPath = Path()
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val fadeTop = Paint(fadePaint)
    private val fadeBottom = Paint(fadePaint)

    private var text: CharSequence = ""
    private var layout: StaticLayout? = null
    private var scrollY = 0f
    private var restoreProgress: Float? = null
    private var lastFrameNanos = 0L
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    var speedLpm = 30
    var playing = false
        private set

    /** Reading progress in 0..1, stable across rebuilds with a different size or text size. */
    val progress: Float get() = progressOf(scrollY, maxScroll())

    init {
        contentDescription = context.getString(R.string.cd_script)
        isClickable = true
        ViewCompat.setStateDescription(this, context.getString(R.string.state_paused))
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!playing) return
            if (lastFrameNanos != 0L) {
                val dt = ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
                scrollY += pxPerSecond(speedLpm, lineHeight()) * dt
                if (scrollY >= maxScroll()) {
                    scrollY = maxScroll()
                    setPlaying(false)
                    invalidate()
                    return
                }
                invalidate()
            }
            lastFrameNanos = frameTimeNanos
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setScript(script: String, sizeSp: Int, startProgress: Float = 0f) {
        val s = SpannableString(script)
        for (r in pauseRanges(script)) {
            s.setSpan(StyleSpan(Typeface.BOLD), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(AMBER), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = s
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat(), resources.displayMetrics)
        restoreProgress = startProgress
        rebuild()
    }

    fun setPlaying(value: Boolean) {
        if (value == playing) return
        if (value && scrollY >= maxScroll()) scrollY = 0f // play at the end starts over
        playing = value
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(frame)
        if (value && isAttachedToWindow) Choreographer.getInstance().postFrameCallback(frame)
        ViewCompat.setStateDescription(
            this, context.getString(if (value) R.string.state_playing else R.string.state_paused),
        )
        invalidate()
        onPlayingChanged?.invoke(value)
    }

    fun restart() {
        setPlaying(false)
        scrollY = 0f
        invalidate()
    }

    private fun lineHeight(): Float {
        val fm = paint.fontMetrics
        return (fm.descent - fm.ascent + fm.leading) * LINE_SPACING
    }

    /** Vertical centre of the line being read, measured from the top of the view. */
    private fun readLineY() = 0.6f * lineHeight() + 8 * density

    private fun maxScroll() = max(0f, (layout?.height ?: 0) - lineHeight())

    private fun rebuild() {
        val w = (width - padLeft - padRight).toInt()
        if (w <= 0) return
        layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, LINE_SPACING)
            .build()
        val pending = restoreProgress
        scrollY = if (pending != null) scrollFor(pending, maxScroll()) else scrollY.coerceIn(0f, maxScroll())
        restoreProgress = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fadeTop.shader = LinearGradient(0f, 0f, 0f, fade, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        fadeBottom.shader = LinearGradient(0f, h - fade, 0f, h.toFloat(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
        rebuild()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (playing) Choreographer.getInstance().postFrameCallback(frame)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frame)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val readY = readLineY()
        val checkpoint = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.withTranslation(padLeft, readY - lineHeight() / 2f - scrollY) { l.draw(this) }
        canvas.drawRect(0f, 0f, w, fade, fadeTop)
        canvas.drawRect(0f, h - fade, w, h, fadeBottom)
        canvas.restoreToCount(checkpoint)

        caretPaint.color = if (playing) AMBER else 0x80FFFFFF.toInt()
        val x = 8 * density
        caretPath.rewind()
        caretPath.moveTo(x, readY - caretSize / 2f)
        caretPath.lineTo(x + caretSize, readY)
        caretPath.lineTo(x, readY + caretSize / 2f)
        caretPath.close()
        canvas.drawPath(caretPath, caretPaint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y; lastY = e.y; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(e.y - downY) > slop) {
                    dragging = true
                    setPlaying(false)
                }
                if (dragging) {
                    scrollY = (scrollY - (e.y - lastY)).coerceIn(0f, maxScroll())
                    invalidate()
                }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> if (!dragging) performClick()
        }
        return true
    }

    // Single entry point for taps and for TalkBack's double-tap.
    override fun performClick(): Boolean {
        setPlaying(!playing)
        return super.performClick()
    }

    companion object {
        val AMBER = "#FFC107".toColorInt()
        private const val LINE_SPACING = 1.15f
    }
}
