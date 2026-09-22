package com.nesco.ytdlpmobile.domain

/** One entry of a job folder. */
data class FileEntry(val name: String, val sizeBytes: Long)

/**
 * The suffixes yt-dlp leaves behind while it works.
 *
 * These are what make resuming possible, so they sit in the folder for as
 * long as the download is unfinished. None of them is ever the result.
 */
private val LEFTOVERS = listOf(".part", ".ytdl", ".temp")

private fun String.isLeftover(): Boolean =
    LEFTOVERS.any { endsWith(it) } || contains(".part-Frag")

/**
 * Read the finished path out of what yt-dlp printed.
 *
 * The download runs with --print after_move:filepath, so the real path is
 * on a line of its own. Everything else on that stream is progress
 * chatter, and every chatter line starts with a bracket.
 */
fun printedFilePath(printed: String): String? = printed.lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .filter { !it.startsWith("[") }
    .lastOrNull { !it.isLeftover() }

/**
 * Pick the finished file out of a job folder.
 *
 * This is the fallback for when nothing was printed. A partial file is
 * often the largest thing in the folder, so size on its own would choose
 * wrongly. The leftovers go first, then size decides.
 */
fun pickFinishedName(files: List<FileEntry>): String? = files
    .filter { !it.name.isLeftover() }
    .maxByOrNull { it.sizeBytes }
    ?.name

/**
 * Turn a yt-dlp failure into a sentence the person can act on.
 *
 * yt-dlp writes for somebody at a console with command line options. On a
 * phone there is neither, so its advice has to be replaced rather than
 * shown.
 */
fun explainFailure(raw: String): String {
    val text = raw.trim()
    val lower = text.lowercase()

    return when {
        lower.contains("not a bot") || lower.contains("sign in to confirm") ->
            "the site asked this phone to prove it is not a robot. That is " +
                "usually the connection rather than the video. Try again on " +
                "mobile data instead of this network, or the other way round."

        lower.contains("private video") ->
            "this video is private, so it cannot be downloaded."

        lower.contains("video unavailable") || lower.contains("removed by the uploader") ->
            "this video is not available any more."

        lower.contains("no space left") || lower.contains("enospc") ->
            "the phone has run out of space. Free some and start it again, " +
                "and the download carries on from where it stopped."

        lower.contains("unsupported url") || lower.contains("is not a valid url") ->
            "that link is not one this app can read."

        text.isEmpty() -> "the download failed, and the reason was not reported."

        // Keep the first line. The rest is a Python traceback, which helps
        // nobody holding a phone.
        else -> text.lineSequence().first { it.isNotBlank() }
            .removePrefix("ERROR: ").trim().take(300)
    }
}

/** yt-dlp writes the rate as "at 1.23MiB/s" on its progress lines. */
private val SPEED = Regex("""\bat\s+([0-9.]+\s*[KMGT]?i?B/s)""")

/**
 * Read the download rate out of a progress line.
 *
 * The library's callback gives percent and time left but no rate, and the
 * web version showed one, so it is taken from the text.
 */
fun parseSpeed(line: String): String? {
    val found = SPEED.find(line)?.groupValues?.get(1)?.replace(" ", "") ?: return null
    // yt-dlp prints this before the first sample arrives.
    return found.takeUnless { it.startsWith("Unknown", ignoreCase = true) }
}
