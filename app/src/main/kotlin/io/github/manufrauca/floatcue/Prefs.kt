package io.github.manufrauca.floatcue

import android.content.Context

/** All persisted state: script, prompter settings and overlay position. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("floatcue", Context.MODE_PRIVATE)

    var script: String
        get() = sp.getString("script", "") ?: ""
        set(v) = sp.edit().putString("script", v).apply()
    var speed: Float
        get() = sp.getFloat("speed", 40f)
        set(v) = sp.edit().putFloat("speed", v).apply()
    var textSize: Int
        get() = sp.getInt("textSize", 30)
        set(v) = sp.edit().putInt("textSize", v).apply()
    var opacity: Int
        get() = sp.getInt("opacity", 70)
        set(v) = sp.edit().putInt("opacity", v).apply()
    var heightPercent: Int
        get() = sp.getInt("heightPercent", 30)
        set(v) = sp.edit().putInt("heightPercent", v).apply()
    /** Overlay top offset in px, or -1 for "just below the status bar / cutout". */
    var overlayY: Int
        get() = sp.getInt("overlayY", -1)
        set(v) = sp.edit().putInt("overlayY", v).apply()
    var notificationPermissionAsked: Boolean
        get() = sp.getBoolean("notifAsked", false)
        set(v) = sp.edit().putBoolean("notifAsked", v).apply()

    companion object {
        const val SPEED_MIN = 10f
        const val SPEED_MAX = 150f
    }
}
