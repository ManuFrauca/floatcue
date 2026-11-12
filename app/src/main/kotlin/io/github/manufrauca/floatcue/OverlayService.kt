package io.github.manufrauca.floatcue

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.view.ViewCompat
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var ui: Context // window context: correct metrics/density for an overlay started from a service
    private lateinit var prefs: Prefs
    private var root: View? = null
    private var prompter: PrompterView? = null
    private var params: WindowManager.LayoutParams? = null
    private var playButton: ImageButton? = null
    private var speedToast: Toast? = null
    private var screenH = 0
    private var topInset = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ui = if (Build.VERSION.SDK_INT >= 30) {
            val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            createDisplayContext(display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else this
        wm = ui.getSystemService(WindowManager::class.java)
        prefs = Prefs(this)
        instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_TOGGLE -> if (root?.visibility == View.VISIBLE) prompter?.let { it.setPlaying(!it.playing) }
            else -> {
                // ServiceCompat drops the specialUse type on API < 34, so this constant is safe on API 26+.
                @SuppressLint("InlinedApi")
                val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                ServiceCompat.startForeground(this, NOTIF_ID, notification(false), type)
                if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
                showOverlay(prompter?.progress ?: 0f)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        instance = null
        removeOverlay()
        super.onDestroy()
    }

    private fun removeOverlay() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; prompter = null; params = null; playButton = null
    }

    /** Called while FloatCue's own settings screen is in front: the strip would only cover it. */
    fun hide() {
        prompter?.setPlaying(false)
        root?.visibility = View.GONE
    }

    /** Called when the settings screen goes away: re-reads every pref, keeps reading progress, stays paused. */
    fun refresh() = showOverlay(prompter?.progress ?: 0f)

    private fun showOverlay(progress: Float) {
        removeOverlay() // restart: re-read settings, never two windows
        val dp = resources.displayMetrics.density
        measureScreen()
        val h = screenH * prefs.heightPercent / 100
        val lp = WindowManager.LayoutParams(
            screenW - (16 * dp).toInt(), h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = clampY(if (prefs.overlayY >= 0) prefs.overlayY else topInset, topInset, screenH, h)
        }
        params = lp

        val p = PrompterView(ui).apply {
            speedLpm = prefs.speedLpm
            val script = prefs.script
            setScript(script.ifBlank { getString(R.string.empty_script_hint) }, prefs.textSize, progress)
            onPlayingChanged = { playing ->
                playButton?.apply {
                    setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                    contentDescription = getString(if (playing) R.string.cd_pause else R.string.cd_play)
                }
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(playing))
            }
        }
        prompter = p

        val row = LinearLayout(ui).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(gripButton(lp))
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(button(R.drawable.ic_slower, R.string.cd_slower) { changeSpeed(0.9f) })
            playButton = button(R.drawable.ic_play, R.string.cd_play) { p.setPlaying(!p.playing) }
            addView(playButton)
            addView(button(R.drawable.ic_faster, R.string.cd_faster) { changeSpeed(1.1f) })
            addView(button(R.drawable.ic_restart, R.string.cd_restart) { p.restart() })
            addView(button(R.drawable.ic_close, R.string.cd_close) { stopSelf() })
        }
        val column = LinearLayout(ui).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb((prefs.opacity * 255 / 100), 0, 0, 0))
                cornerRadius = 20 * dp
                setStroke(dp.toInt().coerceAtLeast(1), 0x1FFFFFFF)
            }
            clipToOutline = true
            addView(p, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * dp).toInt()))
        }
        root = column
        // Never over our own settings screen (it may still be resumed when the service starts).
        if (MainActivity.resumed) column.visibility = View.GONE
        wm.addView(column, lp)
        if (!MainActivity.resumed && progress == 0f) {
            Toast.makeText(this, R.string.toast_tap_to_start, Toast.LENGTH_SHORT).show()
        }
    }

    // The top inset (status bar / cutout) is where the front camera is, so the strip rests right below it.
    private var screenW = 0

    private fun measureScreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            val m = wm.currentWindowMetrics
            screenW = m.bounds.width()
            screenH = m.bounds.height()
            topInset = m.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top
        } else {
            @Suppress("DEPRECATION")
            val dm = resources.displayMetrics
            screenW = dm.widthPixels
            screenH = dm.heightPixels
            @SuppressLint("DiscouragedApi", "InternalInsetResource")
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            topInset = if (id > 0) resources.getDimensionPixelSize(id) else 0
        }
    }

    private fun changeSpeed(factor: Float) {
        val p = prompter ?: return
        p.speedLpm = stepSpeed(p.speedLpm, factor)
        prefs.speedLpm = p.speedLpm
        speedToast?.cancel()
        speedToast = Toast.makeText(this, getString(R.string.toast_speed, p.speedLpm), Toast.LENGTH_SHORT).also { it.show() }
    }

    private fun button(icon: Int, desc: Int, onClick: () -> Unit): ImageButton {
        val size = (48 * resources.displayMetrics.density).toInt()
        return ImageButton(ui).apply {
            setImageResource(icon)
            contentDescription = getString(desc)
            background = ripple()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }
    }

    private fun ripple() = RippleDrawable(
        ColorStateList.valueOf(0x44FFFFFF), null, GradientDrawable().apply { setColor(Color.WHITE); shape = GradientDrawable.OVAL },
    )

    private fun moveTo(lp: WindowManager.LayoutParams, y: Int) {
        lp.y = clampY(y, topInset, screenH, lp.height)
        root?.let { wm.updateViewLayout(it, lp) }
        prefs.overlayY = lp.y
    }

    private fun gripButton(lp: WindowManager.LayoutParams): ImageButton {
        var startY = 0; var startTouch = 0f
        return button(R.drawable.ic_grip, R.string.cd_move) {}.apply {
            setOnClickListener(null)
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startY = lp.y; startTouch = e.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        lp.y = clampY((startY + (e.rawY - startTouch)).roundToInt(), topInset, screenH, lp.height)
                        root?.let { wm.updateViewLayout(it, lp) }
                    }
                    MotionEvent.ACTION_UP -> { prefs.overlayY = lp.y; v.performClick() }
                }
                true
            }
            val step = screenH / 10
            ViewCompat.addAccessibilityAction(this, getString(R.string.action_move_up)) { _, _ -> moveTo(lp, lp.y - step); true }
            ViewCompat.addAccessibilityAction(this, getString(R.string.action_move_down)) { _, _ -> moveTo(lp, lp.y + step); true }
        }
    }

    private fun notification(playing: Boolean): Notification {
        fun action(act: String, label: Int, code: Int) = NotificationCompat.Action(
            0, getString(label),
            PendingIntent.getService(
                this, code, Intent(this, OverlayService::class.java).setAction(act),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .addAction(action(ACTION_TOGGLE, if (playing) R.string.action_pause else R.string.action_play, 1))
            .addAction(action(ACTION_STOP, R.string.action_stop, 2))
            .build()
    }

    companion object {
        private const val CHANNEL = "teleprompter"
        private const val NOTIF_ID = 1
        private const val ACTION_TOGGLE = "io.github.manufrauca.floatcue.TOGGLE"
        const val ACTION_STOP = "io.github.manufrauca.floatcue.STOP"

        /** Set while the service exists; the settings screen uses it as "is the teleprompter running". */
        // Cleared in onDestroy, so it cannot outlive the service.
        @SuppressLint("StaticFieldLeak")
        @Volatile var instance: OverlayService? = null
            private set

        fun start(context: Context) =
            context.startForegroundService(Intent(context, OverlayService::class.java))

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }
    }
}
