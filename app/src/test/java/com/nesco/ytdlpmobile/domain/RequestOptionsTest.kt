package com.nesco.ytdlpmobile.domain

import com.nesco.ytdlpmobile.domain.model.DownloadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The format strings are the web version's, word for word. They are the
 * part that decides what YouTube actually serves.
 */
class RequestOptionsTest {

    private val template = "/data/out/%(title).60B.%(ext)s"

    private fun format(options: List<YtdlpOption>) = options.first { it.key == "-f" }.value

    @Test
    fun `video asks for the best video and audio, merged into mp4`() {
        val options = buildOptions(DownloadMode.VIDEO, template)
        assertEquals("bv*+ba/b", format(options))
        assertTrue(options.any { it.key == "--merge-output-format" && it.value == "mp4" })
    }

    @Test
    fun `a chosen quality caps the height and keeps a way through`() {
        val options = buildOptions(DownloadMode.VIDEO, template, maxHeight = 720)
        assertEquals("bv*[height<=720]+ba/b[height<=720]/bv*+ba/b", format(options))
    }

    @Test
    fun `audio takes the best audio and converts to mp3`() {
        val options = buildOptions(DownloadMode.AUDIO, template)
        assertEquals("ba/b", format(options))
        assertTrue(options.any { it.key == "-x" && it.value == null })
        assertTrue(options.any { it.key == "--audio-format" && it.value == "mp3" })
        assertFalse(options.any { it.key == "--merge-output-format" })
    }

    @Test
    fun `a chosen format never has to be told whether it carries audio`() {
        val options = buildOptions(DownloadMode.FORMAT, template, formatId = "137")
        assertEquals("137+ba/137", format(options))
        assertFalse(options.any { it.key == "--merge-output-format" })
    }

    @Test
    fun `the format mode needs a format id`() {
        val error = runCatching { buildOptions(DownloadMode.FORMAT, template) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `a height that is not positive is refused`() {
        for (bad in listOf(0, -1, -720)) {
            val error = runCatching {
                buildOptions(DownloadMode.VIDEO, template, maxHeight = bad)
            }.exceptionOrNull()
            assertTrue("height $bad was allowed", error is IllegalArgumentException)
        }
    }

    @Test
    fun `the height is ignored for audio and for a chosen format`() {
        assertEquals("ba/b", format(buildOptions(DownloadMode.AUDIO, template, maxHeight = 720)))
        assertEquals(
            "22+ba/22",
            format(buildOptions(DownloadMode.FORMAT, template, formatId = "22", maxHeight = 720))
        )
    }

    @Test
    fun `every mode writes to the template, takes one video and reports the path`() {
        for (mode in DownloadMode.entries) {
            val id = if (mode == DownloadMode.FORMAT) "22" else null
            val options = buildOptions(mode, template, formatId = id)
            assertTrue(options.any { it.key == "-o" && it.value == template })
            assertTrue(options.any { it.key == "--no-playlist" })
            // The extension changes when it converts, so the real path is asked for.
            assertTrue(options.any { it.key == "--print" && it.value == "after_move:filepath" })
        }
    }

    @Test
    fun `it never sets the ffmpeg location, because the library adds it`() {
        for (mode in DownloadMode.entries) {
            val id = if (mode == DownloadMode.FORMAT) "22" else null
            assertFalse(buildOptions(mode, template, formatId = id)
                .any { it.key == "--ffmpeg-location" })
        }
    }
}

/**
 * Reliability is the point of these. yt-dlp resumes from the partial file
 * it left behind, so the job is to keep it trying and to fail cleanly
 * rather than finishing with holes in the media.
 */
class ReliabilityOptionsTest {

    private val template = "/data/out/%(id)s.%(ext)s"

    private fun options(mode: DownloadMode) = buildOptions(
        mode, template, formatId = if (mode == DownloadMode.FORMAT) "22" else null
    )

    private fun values(mode: DownloadMode, key: String) =
        options(mode).filter { it.key == key }.map { it.value }

    @Test
    fun `resuming is asked for rather than assumed`() {
        for (mode in DownloadMode.entries) {
            val keys = options(mode).map { it.key }
            // Both are already the default. Stating them stops a config
            // file on the device from turning resuming off behind us.
            assertTrue(keys.contains("--continue"))
            assertTrue(keys.contains("--part"))
            assertTrue(keys.contains("--ignore-config"))
        }
    }

    @Test
    fun `the three options that would break resuming are never set`() {
        for (mode in DownloadMode.entries) {
            val keys = options(mode).map { it.key }
            assertFalse(keys.contains("--no-part"))
            assertFalse(keys.contains("--no-continue"))
            // This one turns continue_dl off inside yt-dlp as a side effect.
            assertFalse(keys.contains("--force-overwrites"))
        }
    }

    @Test
    fun `retries are bounded and always come with a sleep`() {
        val keys = options(DownloadMode.VIDEO).map { it.key }
        assertTrue(keys.contains("--retries"))
        assertTrue(keys.contains("--fragment-retries"))
        // Without a sleep the retries run back to back and burn out in
        // seconds, which is the gap yt-dlp leaves open by default.
        assertTrue(keys.contains("--retry-sleep"))
        // Unbounded retries against a site answering 429 are a hot loop
        // aimed at a rate limiter. That is what got this rate limited.
        assertFalse(values(DownloadMode.VIDEO, "--retries").contains("infinite"))
        assertFalse(values(DownloadMode.VIDEO, "--fragment-retries").contains("infinite"))
    }

    @Test
    fun `the speed floor is not set, because it forces a full re-extraction`() {
        // Every trip below it aborts and extracts the page again, which
        // multiplies requests at the exact moment the link is weakest.
        assertFalse(options(DownloadMode.VIDEO).any { it.key == "--throttled-rate" })
    }

    @Test
    fun `there is a sleep for each kind of retry`() {
        val sleeps = values(DownloadMode.VIDEO, "--retry-sleep").filterNotNull()
        assertEquals(4, sleeps.size)
        for (type in listOf("http:", "fragment:", "extractor:", "file_access:")) {
            assertTrue("no sleep for $type", sleeps.any { it.startsWith(type) })
        }
    }

    @Test
    fun `a missing fragment stops the download instead of leaving a hole`() {
        for (mode in DownloadMode.entries) {
            val keys = options(mode).map { it.key }
            // The default skips the fragment and reports success, which
            // hands back a file with pieces missing.
            assertTrue(keys.contains("--abort-on-unavailable-fragments"))
            assertFalse(keys.contains("--skip-unavailable-fragments"))
        }
    }

    @Test
    fun `one fragment at a time, so a kill loses the least work`() {
        assertEquals(listOf("1"), values(DownloadMode.VIDEO, "--concurrent-fragments"))
    }

    @Test
    fun `a stalled socket is given up on`() {
        assertEquals(listOf("30"), values(DownloadMode.VIDEO, "--socket-timeout"))
    }

    @Test
    fun `the output name is built from the id, not the title`() {
        // The resume key is the file name. A title can change on the site,
        // and the partial file would then be orphaned.
        assertTrue(STABLE_OUTPUT_NAME.contains("%(id)s"))
        assertFalse(STABLE_OUTPUT_NAME.contains("title"))
    }
}


/** The settings that reach yt-dlp. */
class SettingsOptionsTest {

    private val template = "/data/out/%(id)s.%(ext)s"

    @Test
    fun `the chosen bitrate is used`() {
        val options = buildOptions(DownloadMode.AUDIO, template, audioBitrateK = 320)
        assertTrue(options.any { it.key == "--audio-quality" && it.value == "320K" })
    }

    @Test
    fun `no cookies file means no cookies option`() {
        for (mode in DownloadMode.entries) {
            val id = if (mode == DownloadMode.FORMAT) "22" else null
            assertFalse(
                buildOptions(mode, template, formatId = id).any { it.key == "--cookies" }
            )
        }
    }

    @Test
    fun `a cookies file reaches every mode`() {
        for (mode in DownloadMode.entries) {
            val id = if (mode == DownloadMode.FORMAT) "22" else null
            val options = buildOptions(mode, template, formatId = id, cookiesFile = "/c/x.txt")
            assertTrue(options.any { it.key == "--cookies" && it.value == "/c/x.txt" })
        }
    }

    @Test
    fun `a blank cookies path is ignored`() {
        assertFalse(
            buildOptions(DownloadMode.AUDIO, template, cookiesFile = "  ")
                .any { it.key == "--cookies" }
        )
    }
}

/** Progress has to survive the options that ask for the file path. */
class ProgressVisibilityTest {

    private val template = "/data/out/%(id)s.%(ext)s"

    @Test
    fun `asking for the path does not silence the progress`() {
        for (mode in DownloadMode.entries) {
            val id = if (mode == DownloadMode.FORMAT) "22" else null
            val keys = buildOptions(mode, template, formatId = id).map { it.key }
            assertTrue(keys.contains("--print"))
            // --print implies --quiet, which hides every progress line.
            assertTrue("--no-quiet missing for $mode", keys.contains("--no-quiet"))
            assertTrue(keys.indexOf("--no-quiet") > keys.indexOf("--print"))
        }
    }

    @Test
    fun `nothing else asks for quiet`() {
        val keys = buildOptions(DownloadMode.VIDEO, template).map { it.key }
        assertFalse(keys.contains("--quiet"))
        assertFalse(keys.contains("-q"))
        assertFalse(keys.contains("--no-progress"))
    }
}
