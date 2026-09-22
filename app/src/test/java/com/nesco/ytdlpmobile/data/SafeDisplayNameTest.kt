package com.nesco.ytdlpmobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This is the name the person sees in their music player, and it is built
 * from a title that came off the internet. A title can hold a slash, a
 * colon, a newline or nothing at all.
 */
class SafeDisplayNameTest {

    @Test
    fun `an ordinary title keeps its shape`() {
        assertEquals("A test clip.mp3", safeDisplayName("A test clip", "mp3"))
    }

    @Test
    fun `a title cannot build a path`() {
        val name = safeDisplayName("../../secrets/passwd", "mp4")
        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
        assertTrue(name.endsWith(".mp4"))
    }

    @Test
    fun `the characters a file name may not hold are removed`() {
        val name = safeDisplayName("""a:b*c?d"e<f>g|h""", "mp3")
        for (bad in listOf(":", "*", "?", "\"", "<", ">", "|")) {
            assertFalse("'$bad' survived in $name", name.contains(bad))
        }
    }

    @Test
    fun `newlines and runs of spaces become one space`() {
        assertEquals("a b c.mp3", safeDisplayName("a \n b    c", "mp3"))
    }

    @Test
    fun `a trailing dot is dropped, because it hides the extension`() {
        assertEquals("clip.mp3", safeDisplayName("clip...", "mp3"))
    }

    @Test
    fun `a title of nothing still gives a usable name`() {
        assertEquals("video.mp3", safeDisplayName("", "mp3"))
        assertEquals("video.mp3", safeDisplayName("   ", "mp3"))
        assertEquals("video.mp4", safeDisplayName("///", "mp4"))
    }

    @Test
    fun `a very long title is cut short`() {
        val name = safeDisplayName("x".repeat(400), "mp3")
        assertTrue("the name was $name characters", name.length <= 130)
        assertTrue(name.endsWith(".mp3"))
    }

    @Test
    fun `no extension means no trailing dot`() {
        assertEquals("clip", safeDisplayName("clip", ""))
    }
}
