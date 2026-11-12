package io.github.manufrauca.floatcue

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
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var root: View? = null
    private var prompter: PrompterView? = null
    private var params: WindowManager.LayoutParams? = null
    private var playButton: ImageButton? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_TOGGLE -> prompter?.let { it.setPlaying(!it.playing) }
            else -> {
                ServiceCompat.startForeground(
                    this, NOTIF_ID, notification(false), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
                if (!android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
                running = true
                showOverlay()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        removeOverlay()
        super.onDestroy()
    }

    private fun removeOverlay() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null; prompter = null; params = null; playButton = null
    }

    private fun showOverlay() {
        removeOverlay() // restart: re-read settings, never two windows
        val screenH = screenHeight()
        val h = screenH * prefs.heightPercent / 100
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            y = (if (prefs.overlayY >= 0) prefs.overlayY else statusBarHeight()).coerceIn(0, screenH - h)
        }
        params = lp

        val p = PrompterView(this).apply {
            speedDp = prefs.speed
            val script = prefs.script
            setScript(script.ifBlank { getString(R.string.empty_script_hint) }, prefs.textSize)
            onPlayingChanged = { playing ->
                playButton?.apply {
                    setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
                    contentDescription = getString(if (playing) R.string.cd_pause else R.string.cd_play)
                }
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(playing))
            }
        }
        prompter = p

        val dp = resources.displayMetrics.density
        val row = LinearLayout(this).apply {
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
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb((prefs.opacity * 255 / 100), 0, 0, 0))
                cornerRadius = 16 * dp
            }
            addView(p, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * dp).toInt()))
        }
        root = column
        wm.addView(column, lp)
    }

    private fun screenHeight() = resources.displayMetrics.heightPixels

    // The status bar is as tall as the display cutout on the target device, so this lands next to the camera.
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun changeSpeed(factor: Float) {
        val p = prompter ?: return
        p.speedDp = (p.speedDp * factor).coerceIn(Prefs.SPEED_MIN, Prefs.SPEED_MAX)
        prefs.speed = p.speedDp
    }

    private fun button(icon: Int, desc: Int, onClick: () -> Unit): ImageButton {
        val size = (48 * resources.displayMetrics.density).toInt()
        return ImageButton(this).apply {
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

    private fun gripButton(lp: WindowManager.LayoutParams): ImageButton {
        val screenH = screenHeight()
        var startY = 0; var startTouch = 0f
        return button(R.drawable.ic_grip, R.string.cd_move) {}.apply {
            setOnClickListener(null)
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { startY = lp.y; startTouch = e.rawY }
                    MotionEvent.ACTION_MOVE -> {
                        lp.y = (startY + (e.rawY - startTouch)).roundToInt().coerceIn(0, screenH - lp.height)
                        root?.let { wm.updateViewLayout(it, lp) }
                    }
                    MotionEvent.ACTION_UP -> { prefs.overlayY = lp.y; v.performClick() }
                }
                true
            }
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
            .addAction(action(ACTION_TOGGLE, if (playing) R.string.action_pause else R.string.action_resume, 1))
            .addAction(action(ACTION_STOP, R.string.action_stop, 2))
            .build()
    }

    companion object {
        private const val CHANNEL = "teleprompter"
        private const val NOTIF_ID = 1
        private const val ACTION_TOGGLE = "io.github.manufrauca.floatcue.TOGGLE"
        const val ACTION_STOP = "io.github.manufrauca.floatcue.STOP"

        @Volatile var running = false
            private set

        fun start(context: Context) =
            context.startForegroundService(Intent(context, OverlayService::class.java))

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }
    }
}
