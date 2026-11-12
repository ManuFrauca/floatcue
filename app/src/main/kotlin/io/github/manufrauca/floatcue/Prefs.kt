package io.github.manufrauca.floatcue

import android.content.Context
import androidx.core.content.edit

/** All persisted state: script, prompter settings and overlay position. Numeric values are clamped on read. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("floatcue", Context.MODE_PRIVATE)

    var script: String
        get() = sp.getString("script", "") ?: ""
        set(v) = sp.edit { putString("script", v) }
    /** Reading pace in lines per minute. */
    var speedLpm: Int
        get() = sp.getInt("speedLpm", 30).coerceIn(SPEED_MIN, SPEED_MAX)
        set(v) = sp.edit { putInt("speedLpm", v) }
    var textSize: Int
        get() = sp.getInt("textSize", 30).coerceIn(SIZE_MIN, SIZE_MAX)
        set(v) = sp.edit { putInt("textSize", v) }
    var opacity: Int
        get() = sp.getInt("opacity", 80).coerceIn(OPACITY_MIN, OPACITY_MAX)
        set(v) = sp.edit { putInt("opacity", v) }
    var heightPercent: Int
        get() = sp.getInt("heightPercent", 30).coerceIn(HEIGHT_MIN, HEIGHT_MAX)
        set(v) = sp.edit { putInt("heightPercent", v) }
    /** Overlay top offset in px, or -1 for "just below the status bar / cutout". */
    var overlayY: Int
        get() = sp.getInt("overlayY", -1)
        set(v) = sp.edit { putInt("overlayY", v) }
    var notificationPermissionAsked: Boolean
        get() = sp.getBoolean("notifAsked", false)
        set(v) = sp.edit { putBoolean("notifAsked", v) }
}
