package com.nesco.ytdlpmobile.data

import android.content.Context

/**
 * What the person can change. An interface so the ViewModel is testable.
 */
interface Settings {
    /** Nightly carries YouTube fixes days before stable does. */
    var useNightly: Boolean

    /** MP3 bitrate in kbit/s. */
    var audioBitrateK: Int

    /** Preferred height, or null for the best available. */
    var defaultMaxHeight: Int?

    /** A cookies.txt the person imported, or null. */
    var cookiesPath: String?

    companion object {
        val BITRATES = listOf(128, 192, 256, 320)
        val HEIGHTS = listOf(null, 2160, 1440, 1080, 720, 480, 360)
    }
}

class AndroidSettings(context: Context) : Settings {

    private val prefs =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override var useNightly: Boolean
        get() = prefs.getBoolean(NIGHTLY, false)
        set(value) = prefs.edit().putBoolean(NIGHTLY, value).apply()

    override var audioBitrateK: Int
        get() = prefs.getInt(BITRATE, 192)
        set(value) = prefs.edit().putInt(BITRATE, value).apply()

    override var defaultMaxHeight: Int?
        // Zero stands for "best", because preferences hold no null.
        get() = prefs.getInt(HEIGHT, 0).takeIf { it > 0 }
        set(value) = prefs.edit().putInt(HEIGHT, value ?: 0).apply()

    override var cookiesPath: String?
        get() = prefs.getString(COOKIES, null)
        set(value) = prefs.edit().putString(COOKIES, value).apply()

    private companion object {
        const val NIGHTLY = "nightly"
        const val BITRATE = "audioBitrateK"
        const val HEIGHT = "defaultMaxHeight"
        const val COOKIES = "cookiesPath"
    }
}
