package com.nesco.ytdlpmobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android drops a package's notifications above about five a second. The
 * bar showed no number at all until these rules held the rate down.
 */
class NotificationThrottleTest {

    @Test
    fun `the first post always goes out`() {
        val throttle = NotificationThrottle()
        assertTrue(throttle.accept(NotificationView(0, false), 0))
    }

    @Test
    fun `the same view twice is posted once`() {
        val throttle = NotificationThrottle()
        throttle.accept(NotificationView(5, false), 0)
        assertFalse(throttle.accept(NotificationView(5, false), 10_000))
    }

    @Test
    fun `a new percent inside the gap waits`() {
        val throttle = NotificationThrottle()
        throttle.accept(NotificationView(5, false), 1_000)
        assertFalse(throttle.accept(NotificationView(6, false), 1_100))
    }

    @Test
    fun `a new percent after the gap goes out`() {
        val throttle = NotificationThrottle()
        throttle.accept(NotificationView(5, false), 1_000)
        assertTrue(throttle.accept(NotificationView(6, false), 1_500))
    }

    @Test
    fun `a fast download still posts under the shed limit`() {
        val throttle = NotificationThrottle()
        // yt-dlp reports about twenty times a second on a quick link.
        val posts = (0..200).count { step ->
            throttle.accept(NotificationView(step / 2, false), step * 50L)
        }
        val seconds = 200 * 50L / 1000.0
        assertTrue("posted $posts in $seconds s", posts / seconds < 5.0)
    }

    @Test
    fun `the end of the download is never held back`() {
        val throttle = NotificationThrottle()
        throttle.accept(NotificationView(98, false), 1_000)
        assertTrue(throttle.accept(NotificationView(100, false), 1_001))
    }

    @Test
    fun `the switch to converting is never held back`() {
        val throttle = NotificationThrottle()
        throttle.accept(NotificationView(40, false), 1_000)
        assertTrue(throttle.accept(NotificationView(40, true), 1_001))
    }
}

class NotificationViewTest {

    @Test
    fun `a percent becomes a whole number`() {
        assertEquals(42, notificationView(42.7f, false).percentInt)
    }

    @Test
    fun `no percent stays no percent`() {
        assertNull(notificationView(null, false).percentInt)
    }

    @Test
    fun `a percent outside the range is pulled back in`() {
        assertEquals(100, notificationView(140f, false).percentInt)
        assertEquals(0, notificationView(-3f, false).percentInt)
    }
}
