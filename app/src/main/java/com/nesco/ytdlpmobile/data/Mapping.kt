package com.nesco.ytdlpmobile.data

import com.nesco.ytdlpmobile.domain.model.MediaFormat
import com.nesco.ytdlpmobile.domain.model.VideoDetails
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo

/**
 * The one place that knows the library's shape.
 *
 * The library reports height, fps and the two file sizes as numbers that
 * fall back to zero when the site says nothing, so a missing value and a
 * real zero are the same value. They become null here, once. No rule
 * further in has to remember that.
 */
fun VideoInfo.toDomain(): VideoDetails = VideoDetails(
    id = id ?: displayId ?: "",
    title = title ?: fulltitle ?: "video",
    durationSeconds = duration.takeIf { it > 0 },
    thumbnailUrl = thumbnail ?: thumbnails?.lastOrNull()?.url,
    formats = formats.orEmpty().mapNotNull { it.toDomain() },
)

/** Return null for a format with no id, because nothing can ask for it. */
fun VideoFormat.toDomain(): MediaFormat? {
    val id = formatId ?: return null
    return MediaFormat(
        formatId = id,
        ext = ext,
        height = height.takeIf { it > 0 },
        fps = fps.takeIf { it > 0 },
        videoCodec = vcodec,
        audioCodec = acodec,
        // The exact size first, then the estimate the site offers instead.
        filesize = fileSize.takeIf { it > 0 } ?: fileSizeApproximate.takeIf { it > 0 },
        note = formatNote,
    )
}
