package io.github.manufrauca.floatcue

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.max

/** Scrolling text view. Time-based scrolling via Choreographer; tap toggles, vertical drag scrubs. */
class PrompterView(context: Context) : View(context) {
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val pad = 12 * density

    private var text: CharSequence = ""
    private var layout: StaticLayout? = null
    private var scrollY = 0f
    private var lastFrameNanos = 0L
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    var speedDp = 40f
    var playing = false
        private set

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!playing) return
            if (lastFrameNanos != 0L) {
                val dt = ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
                scrollY += speedDp * density * dt
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

    fun setScript(script: String, sizeSp: Int) {
        val s = SpannableString(script)
        for (r in pauseRanges(script)) {
            s.setSpan(StyleSpan(Typeface.BOLD), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(ForegroundColorSpan(AMBER), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = s
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat(), resources.displayMetrics)
        rebuild()
    }

    fun setPlaying(value: Boolean) {
        if (value == playing) return
        if (value && scrollY >= maxScroll()) scrollY = 0f // play at the end starts over
        playing = value
        lastFrameNanos = 0L
        if (value && isAttachedToWindow) Choreographer.getInstance().postFrameCallback(frame)
        onPlayingChanged?.invoke(value)
    }

    fun restart() {
        scrollY = 0f
        invalidate()
    }

    private fun topPad() = height / 3f

    private fun maxScroll() = max(0f, topPad() + (layout?.height ?: 0) - height / 2f)

    private fun rebuild() {
        val w = (width - 2 * pad).toInt()
        if (w <= 0) return
        layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()
        scrollY = scrollY.coerceIn(0f, maxScroll())
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

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
        canvas.translate(pad, topPad() - scrollY)
        l.draw(canvas)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y; lastY = e.y; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && kotlin.math.abs(e.y - downY) > slop) {
                    dragging = true
                    setPlaying(false)
                }
                if (dragging) {
                    scrollY = (scrollY - (e.y - lastY)).coerceIn(0f, maxScroll())
                    invalidate()
                }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> if (!dragging) {
                setPlaying(!playing)
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        val AMBER = Color.parseColor("#FFC107")
    }
}
