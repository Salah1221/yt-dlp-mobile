package com.nesco.ytdlpmobile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesco.ytdlpmobile.data.DownloadCoordinator
import com.nesco.ytdlpmobile.data.Settings
import com.nesco.ytdlpmobile.data.YtdlpClient
import com.nesco.ytdlpmobile.domain.DownloadSpec
import com.nesco.ytdlpmobile.domain.model.DownloadMode
import com.nesco.ytdlpmobile.domain.model.VideoDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The same steps the web page had. */
enum class Phase { IDLE, CHECKING, READY, DOWNLOADING, CONVERTING, DONE, FAILED }

data class UiState(
    val url: String = "",
    val phase: Phase = Phase.IDLE,
    val details: VideoDetails? = null,
    val selectedHeight: Int? = null,
    val percent: Float? = null,
    val etaSeconds: Long? = null,
    val speed: String? = null,
    val savedAs: String? = null,
    val error: String? = null,
    val showFormats: Boolean = false,
    val ytdlpVersion: String? = null,
    val updating: Boolean = false,
    val updateNote: String? = null,
    val nightly: Boolean = false,
    val audioBitrateK: Int = 192,
    val defaultMaxHeight: Int? = null,
    val cookiesName: String? = null,
) {
    val busy: Boolean get() = phase == Phase.CHECKING ||
        phase == Phase.DOWNLOADING || phase == Phase.CONVERTING
}

class MainViewModel(
    private val client: YtdlpClient,
    private val settings: Settings,
    private val downloads: DownloadCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        _state.update {
            it.copy(
                nightly = settings.useNightly,
                audioBitrateK = settings.audioBitrateK,
                defaultMaxHeight = settings.defaultMaxHeight,
                selectedHeight = settings.defaultMaxHeight,
                cookiesName = settings.cookiesPath?.substringAfterLast('/'),
            )
        }
        viewModelScope.launch {
            val version = runCatching { client.currentVersion() }.getOrNull()
            _state.update { it.copy(ytdlpVersion = version) }
        }
        // The download belongs to the application, so the screen only
        // watches it. Reopening the app picks the state back up.
        viewModelScope.launch {
            downloads.state.collect { job ->
                _state.update { current ->
                    current.copy(
                        phase = when {
                            job.running && job.converting -> Phase.CONVERTING
                            job.running -> Phase.DOWNLOADING
                            job.savedAs != null -> Phase.DONE
                            job.error != null -> Phase.FAILED
                            current.phase == Phase.DOWNLOADING ||
                                current.phase == Phase.CONVERTING -> Phase.READY
                            else -> current.phase
                        },
                        percent = job.percent,
                        etaSeconds = job.etaSeconds,
                        speed = job.speed,
                        savedAs = job.savedAs,
                        error = job.error ?: current.error,
                    )
                }
            }
        }
    }

    fun setNightly(on: Boolean) {
        settings.useNightly = on
        _state.update { it.copy(nightly = on) }
    }

    fun setBitrate(kbits: Int) {
        settings.audioBitrateK = kbits
        _state.update { it.copy(audioBitrateK = kbits) }
    }

    fun setDefaultHeight(height: Int?) {
        settings.defaultMaxHeight = height
        _state.update { it.copy(defaultMaxHeight = height, selectedHeight = height) }
    }

    fun setCookies(path: String?) {
        settings.cookiesPath = path
        _state.update { it.copy(cookiesName = path?.substringAfterLast('/')) }
    }

    /** Fetch a newer yt-dlp. The copy inside the app goes stale in weeks. */
    fun updateYtdlp() {
        if (_state.value.updating) return
        viewModelScope.launch {
            _state.update { it.copy(updating = true, updateNote = null) }
            try {
                val version = client.updateNow(settings.useNightly)
                _state.update { it.copy(ytdlpVersion = version, updateNote = "updated") }
            } catch (error: Exception) {
                _state.update { it.copy(updateNote = error.message) }
            } finally {
                _state.update { it.copy(updating = false) }
            }
        }
    }

    fun onUrlChanged(url: String) = _state.update { it.copy(url = url, error = null) }

    /** Called when another app shares a link into this one. */
    fun onUrlShared(url: String) {
        _state.update { it.copy(url = url, error = null) }
        check()
    }

    fun toggleFormats() = _state.update { it.copy(showFormats = !it.showFormats) }

    fun selectHeight(height: Int?) = _state.update { it.copy(selectedHeight = height) }

    fun dismissError() {
        downloads.clearResult()
        _state.update { it.copy(error = null) }
    }

    fun check() {
        val url = _state.value.url.trim()
        if (url.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            _state.update {
                it.copy(phase = Phase.CHECKING, error = null, details = null, savedAs = null)
            }
            try {
                val details = client.probe(url)
                _state.update {
                    it.copy(
                        phase = Phase.READY,
                        details = details,
                        selectedHeight = settings.defaultMaxHeight,
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(phase = Phase.FAILED, error = error.message) }
            }
        }
    }

    fun download(mode: DownloadMode, formatId: String? = null) {
        val current = _state.value
        val details = current.details ?: return
        if (current.busy) return

        downloads.start(
            DownloadSpec(
                url = current.url.trim(),
                videoId = details.id,
                mode = mode,
                maxHeight = if (mode == DownloadMode.VIDEO) current.selectedHeight else null,
                formatId = formatId,
            ),
            title = details.title,
        )
    }

    fun cancel() = downloads.cancel()
}
