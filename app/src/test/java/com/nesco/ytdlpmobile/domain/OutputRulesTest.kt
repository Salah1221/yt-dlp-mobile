package com.nesco.ytdlpmobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * After a download the real file has to be found. yt-dlp is asked to
 * print it, but the folder also holds the leftovers of resuming, and
 * handing one of those to the person would give them a broken file.
 */
class FinishedFileTest {

    @Test
    fun `the printed path is taken from the last line`() {
        val printed = """
            [download] Destination: /w/abc.f140.m4a
            [download] 100% of 3.50MiB
            /w/abc.mp3
        """.trimIndent()
        assertEquals("/w/abc.mp3", printedFilePath(printed))
    }

    @Test
    fun `progress chatter is not a path`() {
        val printed = "[download] 100% of 3.50MiB in 00:02\n[ExtractAudio] Destination: x"
        assertNull(printedFilePath(printed))
    }

    @Test
    fun `nothing printed gives nothing`() {
        assertNull(printedFilePath(""))
        assertNull(printedFilePath("   \n  \n"))
    }

    @Test
    fun `the leftovers of resuming are never chosen`() {
        val files = listOf(
            FileEntry("abc.mp4.part", 90_000_000),
            FileEntry("abc.mp4.ytdl", 2_000),
            FileEntry("abc.mp4.part-Frag12", 500_000),
            FileEntry("abc.mp4", 4_000_000),
        )
        // The partial file is by far the largest, and it is still wrong.
        assertEquals("abc.mp4", pickFinishedName(files))
    }

    @Test
    fun `the largest finished file wins`() {
        val files = listOf(FileEntry("small.mp4", 10), FileEntry("large.mp4", 100))
        assertEquals("large.mp4", pickFinishedName(files))
    }

    @Test
    fun `a folder holding only leftovers has no finished file`() {
        val files = listOf(FileEntry("abc.mp4.part", 90), FileEntry("abc.mp4.ytdl", 2))
        assertNull(pickFinishedName(files))
    }

    @Test
    fun `an empty folder has no finished file`() {
        assertNull(pickFinishedName(emptyList()))
    }
}

/** The person reads this, so it says what they can do about it. */
class ExplainFailureTest {

    @Test
    fun `the robot check points at the connection, not at the video`() {
        val message = explainFailure(
            "ERROR: [youtube] abc: Sign in to confirm you are not a bot. Use --cookies"
        )
        assertTrue(message.contains("connection", ignoreCase = true))
        // The option it suggests needs a console, which a phone has not got.
        assertFalse(message.contains("--cookies"))
    }

    @Test
    fun `a video that is gone says so`() {
        assertTrue(
            explainFailure("ERROR: [youtube] x: Video unavailable")
                .contains("not available", ignoreCase = true)
        )
    }

    @Test
    fun `a private video says so`() {
        assertTrue(
            explainFailure("ERROR: Private video. Sign in if you have been granted access")
                .contains("private", ignoreCase = true)
        )
    }

    @Test
    fun `a full disk says so, and says the download survives`() {
        val message = explainFailure("OSError: [Errno 28] No space left on device")
        assertTrue(message.contains("space", ignoreCase = true))
        assertTrue(message.contains("carries on", ignoreCase = true))
    }

    @Test
    fun `anything else keeps the first line rather than the whole dump`() {
        val message = explainFailure("ERROR: something odd happened\nTraceback:\n  line 1")
        assertTrue(message.contains("something odd happened"))
        assertFalse(message.contains("Traceback"))
    }

    @Test
    fun `an empty failure still says something`() {
        assertTrue(explainFailure("").isNotBlank())
    }
}

/** The rate the web page showed, taken back out of the text. */
class ParseSpeedTest {

    @Test
    fun `it reads the rate from a progress line`() {
        assertEquals(
            "1.23MiB/s",
            parseSpeed("[download]  42.0% of   10.00MiB at    1.23MiB/s ETA 00:30"),
        )
    }

    @Test
    fun `it handles the other units`() {
        assertEquals("900.00KiB/s", parseSpeed("[download] 1.0% of 1GiB at 900.00KiB/s ETA 10:00"))
        assertEquals("12.00MB/s", parseSpeed("[download] 1.0% of 1GiB at 12.00MB/s ETA 10:00"))
    }

    @Test
    fun `an unknown rate is no rate`() {
        assertNull(parseSpeed("[download]   0.0% of ~10.00MiB at    Unknown B/s ETA Unknown"))
    }

    @Test
    fun `a line with no rate gives nothing`() {
        assertNull(parseSpeed("[youtube] abc: Downloading webpage"))
        assertNull(parseSpeed("[ExtractAudio] Destination: x.mp3"))
        assertNull(parseSpeed(""))
    }
}
