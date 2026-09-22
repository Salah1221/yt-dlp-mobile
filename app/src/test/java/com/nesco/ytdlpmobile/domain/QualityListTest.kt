package com.nesco.ytdlpmobile.domain

import com.nesco.ytdlpmobile.domain.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same rules the web version uses, so the phone offers the same list.
 */
class QualityListTest {

    private fun video(height: Int?, size: Long?, id: String = "v") = MediaFormat(
        formatId = id, ext = "mp4", height = height, fps = 30,
        videoCodec = "avc1", audioCodec = "none", filesize = size, note = ""
    )

    private fun audio(size: Long?, id: String = "a") = MediaFormat(
        formatId = id, ext = "m4a", height = null, fps = null,
        videoCodec = "none", audioCodec = "mp4a", filesize = size, note = ""
    )

    @Test
    fun `it labels the height`() {
        val list = buildQualities(listOf(video(1080, 100), audio(10)))
        assertEquals(1080, list[0].height)
        assertEquals("1080p", list[0].label)
    }

    @Test
    fun `it adds the best audio size to each video size`() {
        val list = buildQualities(listOf(video(1080, 100), audio(10, "a1"), audio(25, "a2")))
        assertEquals(125L, list[0].filesize)
    }

    @Test
    fun `it keeps the largest video of each height`() {
        val list = buildQualities(listOf(video(720, 50, "v1"), video(720, 80, "v2"), audio(10)))
        assertEquals(1, list.size)
        assertEquals(90L, list[0].filesize)
    }

    @Test
    fun `it sorts from high to low`() {
        val list = buildQualities(
            listOf(video(480, 30, "v1"), video(1080, 90, "v2"), video(720, 60, "v3"), audio(10))
        )
        assertEquals(listOf(1080, 720, 480), list.map { it.height })
    }

    @Test
    fun `a video with no reported size gives an entry with no size`() {
        val list = buildQualities(listOf(video(1080, null), audio(10)))
        assertEquals(1080, list[0].height)
        assertNull(list[0].filesize)
    }

    @Test
    fun `it works when no audio size is known`() {
        val list = buildQualities(listOf(video(1080, 100), audio(null)))
        assertEquals(100L, list[0].filesize)
    }

    @Test
    fun `it ignores audio only entries and missing heights`() {
        val list = buildQualities(
            listOf(
                audio(10),
                video(null, 50, "x"),
                MediaFormat("y", "mp4", 720, null, "none", "none", 10, "")
            )
        )
        assertTrue(list.isEmpty())
    }

    @Test
    fun `a height of zero is not a height`() {
        assertTrue(buildQualities(listOf(video(0, 50), audio(10))).isEmpty())
    }
}
