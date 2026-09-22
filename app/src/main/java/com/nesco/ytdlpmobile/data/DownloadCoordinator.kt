package com.nesco.ytdlpmobile.data

import com.nesco.ytdlpmobile.domain.DownloadSpec
import com.nesco.ytdlpmobile.domain.Outcome
import com.nesco.ytdlpmobile.domain.shouldKeepForResume
import com.nesco.ytdlpmobile.domain.workFolderName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What one download is doing. Read by the screen and by the notification. */
data class JobState(
    val running: Boolean = false,
    val converting: Boolean = false,
    val percent: Float? = null,
    val etaSeconds: Long? = null,
    val speed: String? = null,
    val title: String? = null,
    val savedAs: String? = null,
    val error: String? = null,
)

/**
 * Owns the running download.
 *
 * It lives in the application, not in a ViewModel, because a ViewModel
 * dies when its activity goes and the download would go with it. The
 * screen and the notification both read the same state from here.
 */
class DownloadCoordinator(
    private val client: YtdlpClient,
    private val storage: Storage,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(JobState())
    val state: StateFlow<JobState> = _state.asStateFlow()

    private var work: Job? = null
    private var jobId: String? = null

    /** Start a download. A second call while one runs is ignored. */
    fun start(spec: DownloadSpec, title: String) {
        if (_state.value.running) return
        val folder = workFolderName(spec)
        jobId = folder
        _state.value = JobState(running = true, title = title)

        work = scope.launch {
            var outcome = Outcome.FAILED
            try {
                val result = client.download(spec, storage.workDir(folder), folder) { progress ->
                    _state.update { current ->
                        current.copy(
                            converting = current.converting ||
                                isConvertingLine(progress.line),
                            // yt-dlp says nothing on most lines, and a bar
                            // that falls back to zero reads as a fault.
                            percent = progress.percent ?: current.percent,
                            etaSeconds = progress.etaSeconds ?: current.etaSeconds,
                            speed = progress.speed ?: current.speed,
                        )
                    }
                }
                when (result) {
                    is DownloadOutcome.Cancelled -> {
                        outcome = Outcome.CANCELLED
                        _state.value = JobState()
                    }

                    is DownloadOutcome.Finished -> {
                        val name = storage.publish(result.file, title, spec.mode)
                        outcome = Outcome.SUCCEEDED
                        _state.value = JobState(percent = 100f, savedAs = name, title = title)
                    }
                }
            } catch (error: Exception) {
                _state.value = JobState(error = error.message, title = title)
            } finally {
                // A failure and a cancel both keep the partial file, so the
                // next attempt carries on instead of starting again.
                if (!shouldKeepForResume(outcome)) storage.deleteWorkDir(folder)
                jobId = null
            }
        }
    }

    fun cancel() {
        jobId?.let { client.cancel(it) }
    }

    /** Clear a finished or failed result once the screen has shown it. */
    fun clearResult() {
        if (!_state.value.running) _state.value = JobState()
    }
}

/**
 * ffmpeg reports no percent while it converts, so the bar stops pretending
 * to move once one of these appears.
 */
private val CONVERTING_MARKS =
    listOf("[ExtractAudio]", "[Merger]", "[VideoConvertor]", "[Fixup")

fun isConvertingLine(line: String): Boolean =
    CONVERTING_MARKS.any { line.trimStart().startsWith(it) }
