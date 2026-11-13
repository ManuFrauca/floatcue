package io.github.manufrauca.floatcue

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Scrolling text view. Time-based scrolling via Choreographer with eased speed changes; tap toggles,
 * vertical drag scrubs. With [preview] it only draws one static frame (settings screen).
 */
@Suppress("ViewConstructor") // only ever built in code
class PrompterView(
    context: Context,
    private val preview: Boolean = false,
    // The overlay passes its service's resources: a window context ignores the per-app language (Android 13+).
    private val strings: Resources = context.resources,
) : View(context) {
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(2.5f * density, 0f, 1f * density, 0xCC000000.toInt())
    }
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val padLeft = PAD_LEFT_DP * density
    private val padRight = PAD_RIGHT_DP * density
    private val fade = 16 * density
    private val caretSize = 10 * density
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val caretPath = Path()
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
    private val fadeTop = Paint(fadePaint)
    private val fadeBottom = Paint(fadePaint)
    private val readPaint = Paint().apply { color = READ_ALPHA_COLOR; xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val sweepPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private var sweepWidth = 0f
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hudFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; style = Paint.Style.FILL }
    private val hudStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TRACK; style = Paint.Style.STROKE; strokeWidth = density
    }
    private val hudText = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hudRect = RectF()

    private var text: CharSequence = ""
    private var layout: StaticLayout? = null
    private var scrollY = 0f
    private var velocity = 0f
    private var restoreProgress: Float? = null
    private var lastFrameNanos = 0L
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var countdown = 0
    private var hudLabel: String? = null
    private var hudBig = false
    private var hudAtBottom = false
    private var hudAlpha = 0f
    private var hudAnim: ValueAnimator? = null

    var speedLpm = 30
    var playing = false
        private set

    /** Reading progress in 0..1, stable across rebuilds with a different size or text size. */
    val progress: Float get() = progressOf(scrollY, maxScroll())

    init {
        if (preview) {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        } else {
            contentDescription = strings.getString(R.string.cd_script)
            isClickable = true
            ViewCompat.setStateDescription(this, strings.getString(R.string.state_paused))
        }
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameNanos != 0L) {
                val dt = ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
                val target = if (playing) pxPerSecond(speedLpm, lineHeight()) else 0f
                velocity = if (ValueAnimator.areAnimatorsEnabled()) easeToward(velocity, target, dt, EASE_TAU) else target
                scrollY += velocity * dt
                if (scrollY >= maxScroll()) {
                    scrollY = maxScroll()
                    velocity = 0f
                    lastFrameNanos = 0L
                    setPlaying(false)
                    invalidate()
                    return
                }
                invalidate()
            }
            lastFrameNanos = frameTimeNanos
            // Keep going until paused and the ease-out has settled.
            if (playing || abs(velocity) >= 1f) Choreographer.getInstance().postFrameCallback(this)
            else { velocity = 0f; lastFrameNanos = 0L }
        }
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            countdown--
            if (countdown <= 0) {
                hideHud()
                setPlaying(true)
            } else {
                showHud(countdown.toString(), 0, big = true)
                postDelayed(this, 1000)
            }
        }
    }

    fun setScript(script: String, sizeSp: Int, startProgress: Float = 0f) {
        text = styledScript(script)
        paint.textSize = spToPx(resources.displayMetrics, sizeSp)
        restoreProgress = startProgress
        rebuild()
    }

    fun setPlaying(value: Boolean) {
        if (value == playing) return
        if (value && scrollY >= maxScroll()) scrollY = 0f // play at the end starts over
        if (value) hideHud()
        playing = value
        if (isAttachedToWindow && (value || velocity != 0f)) startFrames()
        ViewCompat.setStateDescription(
            this, strings.getString(if (value) R.string.state_playing else R.string.state_paused),
        )
        invalidate()
        onPlayingChanged?.invoke(value)
    }

    private fun startFrames() {
        lastFrameNanos = 0L
        Choreographer.getInstance().removeFrameCallback(frame)
        Choreographer.getInstance().postFrameCallback(frame)
    }

    /** Stops at once: no countdown, no ease-out. Progress is kept. */
    fun halt() {
        cancelCountdown()
        velocity = 0f
        setPlaying(false)
    }

    /**
     * Play/pause. A play request at progress 0 runs a 3-2-1 countdown first; a request during
     * the countdown cancels it.
     */
    fun toggle(haptic: Boolean = true) {
        if (haptic) {
            performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP,
            )
        }
        when {
            countdown > 0 -> { cancelCountdown(); showStartHint() }
            playing -> setPlaying(false)
            else -> {
                if (scrollY >= maxScroll()) scrollY = 0f
                if (scrollY <= 0.5f) startCountdown() else setPlaying(true)
            }
        }
    }

    fun restart() {
        halt()
        scrollY = 0f
        invalidate()
        showStartHint()
    }

    fun showStartHint() = showHud(strings.getString(R.string.hud_tap_to_start), 0)

    private fun startCountdown() {
        countdown = 4
        countdownTick.run()
    }

    private fun cancelCountdown() {
        removeCallbacks(countdownTick)
        if (countdown > 0) hideHud()
        countdown = 0
    }

    /**
     * Shows the pill; [durationMs] <= 0 keeps it until [hideHud]. Timed texts are also announced.
     * [atBottom] puts it just above the buttons, away from the line being read.
     */
    fun showHud(label: String, durationMs: Long = 1200, big: Boolean = false, atBottom: Boolean = false) {
        removeCallbacks(hideHudRunnable)
        hudLabel = label
        hudBig = big
        hudAtBottom = atBottom
        animateHud(1f, 120)
        if (durationMs > 0) {
            postDelayed(hideHudRunnable, durationMs)
            @Suppress("DEPRECATION")
            announceForAccessibility(label)
        }
        invalidate()
    }

    fun hideHud() {
        removeCallbacks(hideHudRunnable)
        animateHud(0f, 200)
    }

    private val hideHudRunnable = Runnable { hideHud() }

    private fun animateHud(to: Float, ms: Long) {
        hudAnim?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled() || hudAlpha == to) {
            hudAlpha = to
            invalidate()
            return
        }
        hudAnim = ValueAnimator.ofFloat(hudAlpha, to).apply {
            duration = ms
            addUpdateListener { hudAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun lineHeight() = lineHeightOf(paint)

    /** Vertical centre of the caret row (the second row), where each line starts being read; from the view's top. */
    private fun readLineY() = 1.5f * lineHeight() + 8 * density

    // The end is one row past the last line, so the read sweep can finish it.
    private fun maxScroll() = (layout?.height ?: 0).toFloat()

    private fun rebuild() {
        val lh = lineHeight()
        // The sweep's soft edge: from grey to full brightness over about one letter, eased at both ends.
        sweepWidth = paint.textSize * SWEEP_EM
        sweepPaint.shader = LinearGradient(-sweepWidth, 0f, 0f, 0f, SWEEP_COLORS, SWEEP_STOPS, Shader.TileMode.CLAMP)
        val w = (width - padLeft - padRight).toInt()
        if (w <= 0) return
        val l = buildLayout(text, paint, w)
        layout = l
        val pending = restoreProgress
        scrollY = when {
            // Static frame: previous line read, the next one partly read.
            preview -> if (l.lineCount > 1) lh * PREVIEW_ROWS else 0f
            pending != null -> scrollFor(pending, maxScroll())
            else -> scrollY.coerceIn(0f, maxScroll())
        }
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
        if (playing || velocity != 0f) startFrames()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frame)
        removeCallbacks(countdownTick)
        removeCallbacks(hideHudRunnable)
        hudAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val lh = lineHeight()
        val readY = readLineY()
        val checkpoint = canvas.saveLayer(0f, 0f, w, h, null)
        val textTop = readY - lh / 2f - scrollY
        canvas.withTranslation(padLeft, textTop) { l.draw(this) }
        drawRead(canvas, l, textTop, w)
        canvas.drawRect(0f, 0f, w, fade, fadeTop)
        canvas.drawRect(0f, h - fade, w, h, fadeBottom)
        canvas.restoreToCount(checkpoint)

        caretPaint.color = if (playing || preview) AMBER else 0x80FFFFFF.toInt()
        val x = 8 * density
        caretPath.rewind()
        caretPath.moveTo(x, readY - caretSize / 2f)
        caretPath.lineTo(x + caretSize, readY)
        caretPath.lineTo(x, readY + caretSize / 2f)
        caretPath.close()
        canvas.drawPath(caretPath, caretPaint)

        if (!preview) {
            drawProgressBar(canvas, w, h)
            drawHud(canvas, w, h)
        }
    }

    /**
     * Greys out what has been read, like a typewriter: every line above the one being read, and that line from
     * the left up to a soft edge. Line i is read while scrollY runs through its own height, so it starts at the
     * caret and is finished one row above it; the sweep follows the scrolling, so it speeds up, slows down,
     * pauses and scrubs with it.
     */
    private fun drawRead(canvas: Canvas, l: StaticLayout, textTop: Float, w: Float) {
        if (scrollY <= 0f) return
        val line = l.getLineForVertical(scrollY.toInt())
        val top = l.getLineTop(line).toFloat()
        val bottom = l.getLineBottom(line).toFloat()
        val bandTop = textTop + top
        if (bandTop > 0f) canvas.drawRect(0f, 0f, w, bandTop, readPaint)
        val x = sweepX(scrollY, top, bottom, padLeft + l.getLineLeft(line), padLeft + l.getLineRight(line), sweepWidth)
        // Move the canvas, not the gradient, so the shader itself never changes from frame to frame.
        canvas.withTranslation(x, 0f) { drawRect(-x, bandTop, w - x, textTop + bottom, sweepPaint) }
    }

    private fun drawProgressBar(canvas: Canvas, w: Float, h: Float) {
        if (maxScroll() <= 0f) return
        val left = padLeft
        val right = w - padRight
        // Top edge: the text there is already faded out, so the bar never reads as an underline, and it sits closest to the lens.
        val top = 5 * density
        val bottom = top + 2 * density
        val r = density
        barPaint.color = TRACK
        canvas.drawRoundRect(left, top, right, bottom, r, r, barPaint)
        barPaint.color = AMBER
        canvas.drawRoundRect(left, top, left + (right - left) * progress, bottom, r, r, barPaint)
    }

    private fun drawHud(canvas: Canvas, w: Float, h: Float) {
        val label = hudLabel ?: return
        if (hudAlpha <= 0f) return
        hudText.textSize = spToPx(resources.displayMetrics, if (hudBig) 16 else 13)
        hudText.typeface = if (hudBig) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-medium", Typeface.NORMAL)
        hudText.color = if (hudBig) AMBER else Color.WHITE
        val fm = hudText.fontMetrics
        val textH = fm.descent - fm.ascent
        // Start hint and countdown: in the band above the reading line, empty at the start.
        // Speed: just above the buttons that changed it, far from the line being read.
        val band = readLineY() - lineHeight() / 2f
        val ph = min(textH + 10 * density, band - 4 * density)
        val pw = max(hudText.measureText(label) + 24 * density, ph)
        val cx = padLeft + (w - padLeft - padRight) / 2f
        val cy = if (hudAtBottom) h - ph / 2f - 6 * density else band / 2f + density
        hudRect.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f)
        val a = (hudAlpha * 255).toInt()
        hudFill.alpha = (0.92f * a).toInt()
        hudStroke.alpha = (0.14f * a).toInt()
        canvas.drawRoundRect(hudRect, ph / 2f, ph / 2f, hudFill)
        canvas.drawRoundRect(hudRect, ph / 2f, ph / 2f, hudStroke)
        hudText.alpha = a
        canvas.drawText(label, cx, hudRect.centerY() - (fm.ascent + fm.descent) / 2f, hudText)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (preview) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y; lastY = e.y; dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(e.y - downY) > slop) {
                    dragging = true
                    halt()
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
        toggle()
        return super.performClick()
    }

    private companion object {
        const val EASE_TAU = 0.22f
        const val READ_ALPHA_COLOR = 0x61000000 // 0.38 alpha: text that has already been read
        const val SWEEP_EM = 1.2f // soft edge width, in text sizes
        const val PREVIEW_ROWS = 1.45f
        // 0.38 to 1 alpha along a smoothstep, so the edge has no visible start or end.
        val SWEEP_STOPS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val SWEEP_COLORS = intArrayOf(0x61000000, 0x7A000000, 0xB0000000.toInt(), 0xE6000000.toInt(), Color.BLACK)
        const val TRACK = 0x24FFFFFF // white at 14 %
    }
}
