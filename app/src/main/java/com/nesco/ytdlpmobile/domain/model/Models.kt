package com.nesco.ytdlpmobile.domain.model

/**
 * The application's own view of a format.
 *
 * The library's VideoFormat carries height, fps and filesize as non null
 * numbers that fall back to 0, so a missing value and a real zero look the
 * same. These are nullable on purpose. The mapper turns 0 into null once,
 * and no rule below it has to know about that trap.
 */
data class MediaFormat(
    val formatId: String,
    val ext: String?,
    val height: Int?,
    val fps: Int?,
    val videoCodec: String?,
    val audioCodec: String?,
    val filesize: Long?,
    val note: String?,
) {
    val hasVideo: Boolean get() = videoCodec != null && videoCodec != NONE
    val hasAudio: Boolean get() = audioCodec != null && audioCodec != NONE
    val isAudioOnly: Boolean get() = !hasVideo && hasAudio

    /** What the list shows for this row, in the words the web page used. */
    val kind: String
        get() = when {
            hasVideo && hasAudio -> "video and audio"
            hasVideo -> "video only"
            else -> "audio only"
        }

    companion object {
        /** yt-dlp writes this when a stream carries no track of that kind. */
        const val NONE = "none"
    }
}

/** One row of the quality list. The size is an estimate. */
data class Quality(
    val height: Int,
    val label: String,
    val filesize: Long?,
)

data class VideoDetails(
    val id: String,
    val title: String,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val formats: List<MediaFormat>,
) {
    val qualities: List<Quality> by lazy { buildQualitiesOf(formats) }
}

/** Kept out of the class body so the rule stays a plain function. */
private fun buildQualitiesOf(formats: List<MediaFormat>): List<Quality> =
    com.nesco.ytdlpmobile.domain.buildQualities(formats)

enum class DownloadMode { VIDEO, AUDIO, FORMAT }
