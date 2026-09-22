package com.nesco.ytdlpmobile.domain

import com.nesco.ytdlpmobile.domain.model.DownloadMode

/** Everything that decides what is downloaded, and therefore where it lands. */
data class DownloadSpec(
    val url: String,
    val videoId: String,
    val mode: DownloadMode,
    val maxHeight: Int? = null,
    val formatId: String? = null,
)

enum class Outcome { SUCCEEDED, FAILED, CANCELLED }

private val SAFE = Regex("[^A-Za-z0-9_-]")

/**
 * Return the folder this download works in.
 *
 * It must be the same every time for the same download, because that is
 * how yt-dlp finds the partial file it left behind and carries on instead
 * of starting again. It must differ when the wanted file differs, or a
 * 720p run would resume a 1080p partial.
 */
fun workFolderName(spec: DownloadSpec): String {
    val id = spec.videoId.replace(SAFE, "").take(40)
    // Some sites report no id. The URL is then the only stable thing there
    // is, so its hash stands in for one.
    val stem = id.ifEmpty { "u" + spec.url.hashCode().toUInt().toString(16) }
    val what = when (spec.mode) {
        DownloadMode.VIDEO -> "video" + (spec.maxHeight?.let { "-$it" } ?: "-best")
        DownloadMode.AUDIO -> "audio"
        DownloadMode.FORMAT -> "format-" + (spec.formatId ?: "").replace(SAFE, "")
    }
    return "$stem-$what"
}

/**
 * Return true when the work folder must survive.
 *
 * Only a success is finished. A failure is exactly the case resuming
 * exists for, and a cancel is a pause rather than a refusal, so both keep
 * the partial file. Old folders are swept by age instead, the way the
 * server version swept its jobs.
 */
fun shouldKeepForResume(outcome: Outcome): Boolean = outcome != Outcome.SUCCEEDED
