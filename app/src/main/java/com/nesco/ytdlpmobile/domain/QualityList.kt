package com.nesco.ytdlpmobile.domain

import com.nesco.ytdlpmobile.domain.model.MediaFormat
import com.nesco.ytdlpmobile.domain.model.Quality

/**
 * Turn the format list into one row for each video height.
 *
 * The rules are the web version's, so the phone offers the same list:
 * keep the formats that carry video and have a real height, group them by
 * height, take the largest reported size in each group, add the largest
 * audio size, and sort from high to low.
 *
 * The size is an estimate. It leaves out the container overhead, and a
 * site does not always report one. A format with no size gives a row with
 * no size, and the screen then shows the height alone.
 */
fun buildQualities(formats: List<MediaFormat>): List<Quality> {
    val bestAudio = formats
        .filter { it.isAudioOnly }
        .mapNotNull { it.filesize }
        .filter { it > 0 }
        .maxOrNull() ?: 0L

    val largestOfEachHeight = LinkedHashMap<Int, Long?>()
    for (format in formats) {
        if (!format.hasVideo) continue
        val height = format.height ?: continue
        if (height <= 0) continue
        val known = largestOfEachHeight[height]
        if (!largestOfEachHeight.containsKey(height) ||
            (format.filesize ?: 0L) > (known ?: 0L)
        ) {
            largestOfEachHeight[height] = format.filesize
        }
    }

    return largestOfEachHeight.keys.sortedDescending().map { height ->
        val videoSize = largestOfEachHeight[height]
        Quality(
            height = height,
            label = "${height}p",
            filesize = if (videoSize != null && videoSize > 0) videoSize + bestAudio else null,
        )
    }
}
