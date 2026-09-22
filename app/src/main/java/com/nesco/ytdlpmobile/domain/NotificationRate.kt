package com.nesco.ytdlpmobile.domain

/** The only two things the progress notification shows. */
data class NotificationView(val percentInt: Int?, val converting: Boolean)

fun notificationView(percent: Float?, converting: Boolean): NotificationView =
    NotificationView(percent?.toInt()?.coerceIn(0, 100), converting)

/**
 * Decides when to post the progress notification.
 *
 * Android sheds notifications above about five posts a second per package,
 * and yt-dlp reports progress far faster than that. Every post after the
 * limit was dropped, so the bar kept the indeterminate state it started
 * with and no number ever appeared.
 */
class NotificationThrottle(private val minGapMs: Long = MIN_GAP_MS) {

    private var last: NotificationView? = null
    private var lastAtMs: Long? = null

    /** True when the caller must post now. A true answer is recorded. */
    fun accept(view: NotificationView, nowMs: Long): Boolean {
        if (view == last) return false
        // 100% and the switch to converting are the last words on a job, so
        // they go out whatever the gap. A dropped one stays wrong for ever.
        val urgent = view.percentInt == 100 || view.converting
        val since = lastAtMs?.let { nowMs - it }
        if (!urgent && since != null && since < minGapMs) return false
        last = view
        lastAtMs = nowMs
        return true
    }

    companion object {
        /** Two and a half posts a second, safely under the shed limit. */
        const val MIN_GAP_MS = 400L
    }
}
