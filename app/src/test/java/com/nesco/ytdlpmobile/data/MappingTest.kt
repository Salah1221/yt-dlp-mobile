package com.nesco.ytdlpmobile.data

import com.fasterxml.jackson.databind.ObjectMapper
import com.yausername.youtubedl_android.mapper.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The library reports height, fps and filesize as numbers that fall back
 * to zero, so a missing value and a real zero look the same. Everything
 * here guards that boundary. Below it, the rules see null and nothing
 * has to remember the trap.
 */
class MappingTest {

    private val json = """
    {
      "id": "abc123",
      "title": "A test clip",
      "duration": 214,
      "thumbnail": "https://example.com/t.jpg",
      "formats": [
        {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a.40.2",
         "filesize":3500000,"format_note":"medium"},
        {"format_id":"137","ext":"mp4","vcodec":"avc1.640028","acodec":"none",
         "height":1080,"fps":30,"filesize":52000000},
        {"format_id":"136","ext":"mp4","vcodec":"avc1.4d401f","acodec":"none",
         "height":720,"fps":30,"filesize_approx":26000000},
        {"format_id":"storyboard","ext":"mhtml","vcodec":"none","acodec":"none"},
        {"format_id":"18","ext":"mp4","vcodec":"avc1.42001E","acodec":"mp4a.40.2",
         "height":360,"fps":24,"filesize":9000000}
      ]
    }
    """.trimIndent()

    private fun parse(): VideoInfo = ObjectMapper().readValue(json, VideoInfo::class.java)

    @Test
    fun `it reads the title, the length and the thumbnail`() {
        val details = parse().toDomain()
        assertEquals("abc123", details.id)
        assertEquals("A test clip", details.title)
        assertEquals(214, details.durationSeconds)
        assertEquals("https://example.com/t.jpg", details.thumbnailUrl)
    }

    @Test
    fun `a missing height becomes null rather than zero`() {
        val audio = parse().toDomain().formats.first { it.formatId == "140" }
        assertNull(audio.height)
        assertNull(audio.fps)
        assertTrue(audio.isAudioOnly)
    }

    @Test
    fun `an approximate size is used when no exact size is given`() {
        val format = parse().toDomain().formats.first { it.formatId == "136" }
        assertEquals(26000000L, format.filesize)
    }

    @Test
    fun `a format with no size at all has no size`() {
        val format = parse().toDomain().formats.first { it.formatId == "storyboard" }
        assertNull(format.filesize)
        assertFalse(format.hasVideo)
        assertFalse(format.hasAudio)
    }

    @Test
    fun `a format that carries both is reported as both`() {
        val format = parse().toDomain().formats.first { it.formatId == "18" }
        assertTrue(format.hasVideo)
        assertTrue(format.hasAudio)
        assertEquals("video and audio", format.kind)
    }

    @Test
    fun `the quality list comes out of the real payload`() {
        val qualities = parse().toDomain().qualities
        assertEquals(listOf(1080, 720, 360), qualities.map { it.height })
        // 52,000,000 for the video plus 3,500,000 for the best audio.
        assertEquals(55500000L, qualities.first().filesize)
        assertEquals("1080p", qualities.first().label)
    }

    @Test
    fun `a payload with no formats does not fall over`() {
        val empty = ObjectMapper().readValue("""{"id":"x","title":"y"}""", VideoInfo::class.java)
        val details = empty.toDomain()
        assertTrue(details.formats.isEmpty())
        assertTrue(details.qualities.isEmpty())
        // duration 0 means unknown, not a zero length video.
        assertNull(details.durationSeconds)
    }
}
