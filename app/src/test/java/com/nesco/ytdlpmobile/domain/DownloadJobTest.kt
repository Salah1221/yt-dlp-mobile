package com.nesco.ytdlpmobile.domain

import com.nesco.ytdlpmobile.domain.model.DownloadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resuming is the whole point. yt-dlp continues from the partial file it
 * left behind, so two things have to hold: the same download must land in
 * the same folder every time, and the partial file must survive anything
 * that is not a success.
 */
class DownloadJobTest {

    private fun spec(
        id: String = "dBLqj8XT_c4",
        mode: DownloadMode = DownloadMode.VIDEO,
        height: Int? = null,
        formatId: String? = null,
    ) = DownloadSpec(
        url = "https://www.youtube.com/watch?v=$id",
        videoId = id,
        mode = mode,
        maxHeight = height,
        formatId = formatId,
    )

    @Test
    fun `the same download always gets the same folder`() {
        assertEquals(workFolderName(spec()), workFolderName(spec()))
    }

    @Test
    fun `a different quality gets its own folder`() {
        // Resuming a 1080p partial into a 720p download would be wrong.
        assertNotEquals(
            workFolderName(spec(height = 1080)),
            workFolderName(spec(height = 720)),
        )
    }

    @Test
    fun `audio and video do not share a folder`() {
        assertNotEquals(
            workFolderName(spec(mode = DownloadMode.VIDEO)),
            workFolderName(spec(mode = DownloadMode.AUDIO)),
        )
    }

    @Test
    fun `two chosen formats do not share a folder`() {
        assertNotEquals(
            workFolderName(spec(mode = DownloadMode.FORMAT, formatId = "137")),
            workFolderName(spec(mode = DownloadMode.FORMAT, formatId = "136")),
        )
    }

    @Test
    fun `two videos do not share a folder`() {
        assertNotEquals(workFolderName(spec(id = "aaa")), workFolderName(spec(id = "bbb")))
    }

    @Test
    fun `the name is safe to use as a folder`() {
        val name = workFolderName(spec(id = "../../etc/pa ss:wd*?"))
        for (bad in listOf("/", "\\", ":", "*", "?", " ", "..")) {
            assertFalse("'$bad' survived in $name", name.contains(bad))
        }
        assertTrue(name.isNotEmpty())
    }

    @Test
    fun `a video with no id still gets a stable folder`() {
        // Some sites report no id. The URL is then the only stable thing.
        val one = spec(id = "").copy(videoId = "")
        assertEquals(workFolderName(one), workFolderName(one))
        assertTrue(workFolderName(one).isNotEmpty())
    }

    @Test
    fun `a success clears the folder and anything else keeps it`() {
        assertFalse(shouldKeepForResume(Outcome.SUCCEEDED))
        // A failure is the case resuming exists for.
        assertTrue(shouldKeepForResume(Outcome.FAILED))
        // A cancel is a pause. Pressing download again should carry on.
        assertTrue(shouldKeepForResume(Outcome.CANCELLED))
    }
}
