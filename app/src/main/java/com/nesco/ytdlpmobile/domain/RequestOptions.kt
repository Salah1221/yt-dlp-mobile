package com.nesco.ytdlpmobile.domain

import com.nesco.ytdlpmobile.domain.model.DownloadMode

/**
 * The file name yt-dlp writes to, inside the job's own folder.
 *
 * The resume key is the file name. yt-dlp looks for <name>.part and
 * <name>.ytdl, so the name has to be identical on the second run. A title
 * can change on the site, which would orphan the partial file, so the id
 * is used here. The friendly name is given when the file is copied out.
 */
const val STABLE_OUTPUT_NAME = "%(id)s.%(ext)s"

/** One yt-dlp option. A flag has no value. */
data class YtdlpOption(val key: String, val value: String? = null)

/**
 * Build the options for one download.
 *
 * The format strings are the web version's, word for word, because they
 * are the part that decides what YouTube actually serves.
 *
 * Never add --ffmpeg-location here. The library appends it to every call,
 * and yt-dlp options append rather than replace, so ours would become a
 * second conflicting value rather than an override.
 */
fun buildOptions(
    mode: DownloadMode,
    outputTemplate: String,
    formatId: String? = null,
    maxHeight: Int? = null,
    audioBitrateK: Int = 192,
    cookiesFile: String? = null,
): List<YtdlpOption> {
    require(maxHeight == null || maxHeight > 0) {
        "the height must be more than zero, not $maxHeight"
    }
    require(mode != DownloadMode.FORMAT || !formatId.isNullOrBlank()) {
        "the format mode needs a format id"
    }

    val options = mutableListOf<YtdlpOption>()
    options += YtdlpOption("-o", outputTemplate)
    options += YtdlpOption("--no-playlist")
    // The extension changes when it converts, so ask for the real path
    // rather than working it out from the template.
    options += YtdlpOption("--print", "after_move:filepath")
    // --print implies --quiet, which silences every progress line, so the
    // bar would never move. This puts the progress back. It must come
    // after --print.
    options += YtdlpOption("--no-quiet")
    // One progress line at a time, which is what the reader parses.
    options += YtdlpOption("--newline")
    options += reliabilityOptions()
    // The one lever against the robot check on a phone. There is no
    // browser to read cookies from, so the file is imported instead.
    if (!cookiesFile.isNullOrBlank()) options += YtdlpOption("--cookies", cookiesFile)

    when (mode) {
        DownloadMode.VIDEO -> {
            options += YtdlpOption(
                "-f",
                if (maxHeight != null) {
                    // The last branch is a safety net. The list only offers a
                    // height the video has, so it should never be needed.
                    "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]/bv*+ba/b"
                } else {
                    "bv*+ba/b"
                },
            )
            options += YtdlpOption("--merge-output-format", "mp4")
        }

        DownloadMode.AUDIO -> {
            options += YtdlpOption("-f", "ba/b")
            options += YtdlpOption("-x")
            options += YtdlpOption("--audio-format", "mp3")
            options += YtdlpOption("--audio-quality", "${audioBitrateK}K")
        }

        // yt-dlp tries the format with the best audio first. If the format
        // already carries audio that attempt fails and the fallback runs, so
        // nothing here has to know which it is.
        DownloadMode.FORMAT -> options += YtdlpOption("-f", "$formatId+ba/$formatId")
    }

    return options
}

/**
 * The options that keep a download going on a connection that drops.
 *
 * yt-dlp resumes from its partial file by itself, so the work here is to
 * keep it trying long enough for the link to come back, and to fail
 * cleanly rather than hand back a file with pieces missing.
 */
private fun reliabilityOptions(): List<YtdlpOption> = listOf(
    // No configuration file on the device may turn the next two off.
    YtdlpOption("--ignore-config"),
    // Both are already the default. Saying them keeps them that way.
    YtdlpOption("--continue"),
    YtdlpOption("--part"),

    // Bounded on purpose. Unbounded retries against a site that answers
    // 429 are a hot loop aimed at a rate limiter, which is what earned a
    // 429 here. The partial file survives, so a later attempt resumes.
    YtdlpOption("--retries", "10"),
    YtdlpOption("--fragment-retries", "10"),
    YtdlpOption("--extractor-retries", "3"),
    YtdlpOption("--file-access-retries", "10"),

    // Without these there is no delay at all between retries. Endless
    // retries would then be a hot loop that empties the battery.
    YtdlpOption("--retry-sleep", "http:exp=1:60"),
    YtdlpOption("--retry-sleep", "fragment:exp=1:60"),
    YtdlpOption("--retry-sleep", "extractor:exp=1:30"),
    YtdlpOption("--retry-sleep", "file_access:linear=1::2"),

    YtdlpOption("--socket-timeout", "30"),
    // The default skips a fragment it cannot fetch and calls the download
    // a success, which hands back media with holes in it. A clean failure
    // can be retried against the same partial file.
    YtdlpOption("--abort-on-unavailable-fragments"),
    // One at a time loses the least work when the process is killed.
    YtdlpOption("--concurrent-fragments", "1"),
)
