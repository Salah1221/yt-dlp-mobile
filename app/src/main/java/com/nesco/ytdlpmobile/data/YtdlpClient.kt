package com.nesco.ytdlpmobile.data

import android.content.Context
import android.util.Log
import com.nesco.ytdlpmobile.domain.DownloadSpec
import com.nesco.ytdlpmobile.domain.FileEntry
import com.nesco.ytdlpmobile.domain.STABLE_OUTPUT_NAME
import com.nesco.ytdlpmobile.domain.buildOptions
import com.nesco.ytdlpmobile.domain.explainFailure
import com.nesco.ytdlpmobile.domain.model.VideoDetails
import com.nesco.ytdlpmobile.domain.parseSpeed
import com.nesco.ytdlpmobile.domain.pickFinishedName
import com.nesco.ytdlpmobile.domain.printedFilePath
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One progress report. A null value means yt-dlp has not said yet. */
data class Progress(
    val percent: Float?,
    val etaSeconds: Long?,
    val speed: String?,
    val line: String,
)

sealed interface DownloadOutcome {
    data class Finished(val file: File) : DownloadOutcome
    data object Cancelled : DownloadOutcome
}

/** A failure already turned into a sentence for the person. */
class YtdlpFailure(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Everything the app asks of yt-dlp.
 *
 * It is an interface so the ViewModel can be tested against a fake, with
 * no device, no network and no waiting.
 */
interface YtdlpClient {
    suspend fun ensureReady()
    suspend fun probe(url: String): VideoDetails
    suspend fun download(
        spec: DownloadSpec,
        workDir: File,
        jobId: String,
        onProgress: (Progress) -> Unit,
    ): DownloadOutcome

    /** Stop a running download. The work folder is left alone on purpose. */
    fun cancel(jobId: String): Boolean

    /** The yt-dlp in use, or null when no update has ever succeeded. */
    suspend fun currentVersion(): String?

    /** Fetch a newer yt-dlp. Returns the version now in use. */
    suspend fun updateNow(nightly: Boolean = false): String?
}

class RealYtdlpClient(context: Context, private val settings: Settings) : YtdlpClient {

    private val appContext = context.applicationContext
    private val startUp = Mutex()
    private var ready = false
    private var triedUpdate = false

    /**
     * The library demands this order: yt-dlp first, then ffmpeg, and only
     * then the update, which refuses to run before init. All three block,
     * so none of them may touch the main thread.
     */
    override suspend fun ensureReady(): Unit = withContext(Dispatchers.IO) {
        startUp.withLock {
            if (!ready) {
                try {
                    YoutubeDL.getInstance().init(appContext)
                    FFmpeg.getInstance().init(appContext)
                } catch (error: Exception) {
                    throw YtdlpFailure(
                        "the downloader could not start up on this phone.", error,
                    )
                }
                ready = true
            }
            // The copy inside the APK is from 2025-11-12 and YouTube
            // changes weekly, so this is what keeps the app working. Once
            // per process only: asking on every download is the request
            // pattern that earns a rate limit.
            if (!triedUpdate) {
                triedUpdate = true
                runCatching { YoutubeDL.getInstance().updateYoutubeDL(appContext) }
                    .onSuccess { Log.i("YTDLP", "update: $it, now on ${version()}") }
                    .onFailure { Log.w("YTDLP", "update failed, on ${version()}: ${it.message}") }
            }
        }
    }

    override suspend fun probe(url: String): VideoDetails = withContext(Dispatchers.IO) {
        ensureReady()
        try {
            // getInfo adds --dump-json and runs with no process id, so it
            // cannot be cancelled. It is short, and the download is the
            // part that needs stopping.
            YoutubeDL.getInstance().getInfo(url).toDomain()
        } catch (error: YtdlpFailure) {
            throw error
        } catch (error: Exception) {
            Log.w("YTDLP", "probe failed: " + error.message)
            throw YtdlpFailure(explainFailure(error.message.orEmpty()), error)
        }
    }

    override suspend fun download(
        spec: DownloadSpec,
        workDir: File,
        jobId: String,
        onProgress: (Progress) -> Unit,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        ensureReady()
        workDir.mkdirs()

        val request = YoutubeDLRequest(spec.url)
        val template = File(workDir, STABLE_OUTPUT_NAME).absolutePath
        val options = buildOptions(
            mode = spec.mode,
            outputTemplate = template,
            formatId = spec.formatId,
            maxHeight = spec.maxHeight,
            audioBitrateK = settings.audioBitrateK,
            cookiesFile = settings.cookiesPath,
        )
        for (option in options) {
            if (option.value == null) request.addOption(option.key)
            else request.addOption(option.key, option.value)
        }

        try {
            val response = YoutubeDL.getInstance().execute(request, jobId) { percent, eta, line ->
                // Both sit at -1 until a line parses as progress, and the
                // percent is on a scale of 0 to 100.
                onProgress(
                    Progress(
                        percent = percent.takeIf { it >= 0f },
                        etaSeconds = eta.takeIf { it >= 0L },
                        speed = parseSpeed(line),
                        line = line,
                    )
                )
            }
            val finished = finishedFile(response.out, workDir)
                ?: throw YtdlpFailure("the download finished but produced no file.")
            DownloadOutcome.Finished(finished)
        } catch (error: YoutubeDL.CanceledException) {
            DownloadOutcome.Cancelled
        } catch (error: YtdlpFailure) {
            throw error
        } catch (error: Exception) {
            Log.w("YTDLP", "download failed: " + error.message)
            throw YtdlpFailure(explainFailure(error.message.orEmpty()), error)
        }
    }

    override suspend fun currentVersion(): String? = withContext(Dispatchers.IO) { version() }

    override suspend fun updateNow(nightly: Boolean): String? = withContext(Dispatchers.IO) {
        ensureReady()
        val channel = if (nightly) {
            YoutubeDL.UpdateChannel.NIGHTLY
        } else {
            YoutubeDL.UpdateChannel.STABLE
        }
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(appContext, channel)
            Log.i("YTDLP", "manual update: $status, now on ${version()}")
        } catch (error: Exception) {
            Log.w("YTDLP", "manual update failed: ${error.message}")
            throw YtdlpFailure(
                "could not reach the update server. The app keeps the " +
                    "version it has.", error,
            )
        }
        version()
    }

    private fun version(): String? =
        runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()

    override fun cancel(jobId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(jobId) }.getOrDefault(false)

    /** Take the printed path, or fall back to reading the folder. */
    private fun finishedFile(printed: String, workDir: File): File? {
        printedFilePath(printed)?.let { path ->
            val file = File(path)
            if (file.isFile) return file
        }
        val entries = workDir.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .map { FileEntry(it.name, it.length()) }
        return pickFinishedName(entries)?.let { File(workDir, it) }
    }
}
