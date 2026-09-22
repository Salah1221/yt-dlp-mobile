package com.nesco.ytdlpmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nesco.ytdlpmobile.domain.model.MediaFormat
import com.nesco.ytdlpmobile.domain.model.Quality
import com.nesco.ytdlpmobile.domain.model.VideoDetails
import com.nesco.ytdlpmobile.ui.theme.YTDLPMobileTheme
import java.util.Locale
import kotlin.math.roundToInt

/** Every control is at least this high, so a thumb can hit it. */
private val TapHeight = 48.dp

/**
 * The whole screen. It holds no state of its own except the open or
 * closed state of the quality menu.
 */
@Composable
fun MainScreen(
    state: UiState,
    onUrlChanged: (String) -> Unit,
    onCheck: () -> Unit,
    onSelectHeight: (Int?) -> Unit,
    onDownloadVideo: () -> Unit,
    onDownloadAudio: () -> Unit,
    onDownloadFormat: (String) -> Unit,
    onToggleFormats: () -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The window draws edge to edge, so the status bar and the
            // gesture bar would sit on top of the content without this.
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UrlBar(
            url = state.url,
            canCheck = state.url.isNotBlank() && !state.busy,
            checking = state.phase == Phase.CHECKING,
            onUrlChanged = onUrlChanged,
            onCheck = onCheck,
        )

        state.error?.let { message ->
            ErrorArea(message = message, onDismissError = onDismissError)
        }

        val details = state.details
        if (details != null) {
            VideoHeader(details = details)

            if (details.qualities.isNotEmpty()) {
                QualityRow(
                    qualities = details.qualities,
                    selectedHeight = state.selectedHeight,
                    enabled = !state.busy,
                    onSelectHeight = onSelectHeight,
                )
            }

            ActionButtons(
                enabled = !state.busy,
                onDownloadVideo = onDownloadVideo,
                onDownloadAudio = onDownloadAudio,
                onToggleFormats = onToggleFormats,
            )

            if (state.showFormats) {
                FormatList(
                    formats = details.formats,
                    enabled = !state.busy,
                    onDownloadFormat = onDownloadFormat,
                )
            }
        }

        if (state.phase == Phase.DOWNLOADING || state.phase == Phase.CONVERTING) {
            ProgressArea(
                converting = state.phase == Phase.CONVERTING,
                percent = state.percent,
                etaSeconds = state.etaSeconds,
                onCancel = onCancel,
            )
        }

        if (state.phase == Phase.DONE) {
            SavedLine(savedAs = state.savedAs)
        }
    }
}

@Composable
private fun UrlBar(
    url: String,
    canCheck: Boolean,
    checking: Boolean,
    onUrlChanged: (String) -> Unit,
    onCheck: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChanged,
            label = { Text("Video link") },
            placeholder = { Text("https://") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onCheck,
            enabled = canCheck,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TapHeight),
        ) {
            Text(if (checking) "Checking" else "Check")
        }
    }
}

@Composable
private fun ErrorArea(message: String, onDismissError: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
            TextButton(
                onClick = onDismissError,
                modifier = Modifier
                    .align(Alignment.End)
                    .heightIn(min = TapHeight),
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun VideoHeader(details: VideoDetails) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (details.thumbnailUrl != null) {
                AsyncImage(
                    model = details.thumbnailUrl,
                    // The title is already shown below, so the picture adds
                    // nothing for a screen reader.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = "No thumbnail",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = details.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            // A long title must not push the buttons off the screen.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        details.durationSeconds?.let { seconds ->
            Text(
                text = formatDuration(seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QualityRow(
    qualities: List<Quality>,
    selectedHeight: Int?,
    enabled: Boolean,
    onSelectHeight: (Int?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selected = qualities.firstOrNull { it.height == selectedHeight }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Quality", style = MaterialTheme.typography.bodyMedium)
        Box(modifier = Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { open = true },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TapHeight),
            ) {
                Text(
                    text = if (selected != null) qualityText(selected) else "Best quality",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(
                    text = { Text("Best quality") },
                    onClick = {
                        open = false
                        onSelectHeight(null)
                    },
                )
                qualities.forEach { quality ->
                    DropdownMenuItem(
                        text = { Text(qualityText(quality)) },
                        onClick = {
                            open = false
                            onSelectHeight(quality.height)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButtons(
    enabled: Boolean,
    onDownloadVideo: () -> Unit,
    onDownloadAudio: () -> Unit,
    onToggleFormats: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onDownloadVideo,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TapHeight),
        ) {
            Text("Download video (MP4)")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // These two are tonal, so they stay quieter than the button above.
            FilledTonalButton(
                onClick = onDownloadAudio,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TapHeight),
            ) {
                Text("Audio MP3")
            }
            FilledTonalButton(
                onClick = onToggleFormats,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = TapHeight),
            ) {
                Text("All formats")
            }
        }
    }
}

@Composable
private fun FormatList(
    formats: List<MediaFormat>,
    enabled: Boolean,
    onDownloadFormat: (String) -> Unit,
) {
    // A Column, not a LazyColumn. The page already scrolls as one piece.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            formats.forEachIndexed { index, format ->
                if (index > 0) HorizontalDivider()
                FormatRow(
                    format = format,
                    enabled = enabled,
                    onDownloadFormat = onDownloadFormat,
                )
            }
        }
    }
}

@Composable
private fun FormatRow(
    format: MediaFormat,
    enabled: Boolean,
    onDownloadFormat: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatName(format),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${format.kind}, ${sizeText(format.filesize)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = { onDownloadFormat(format.formatId) },
            enabled = enabled,
            modifier = Modifier.heightIn(min = TapHeight),
        ) {
            Text("Get")
        }
    }
}

@Composable
private fun ProgressArea(
    converting: Boolean,
    percent: Float?,
    etaSeconds: Long?,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (converting) "Converting" else "Downloading",
                style = MaterialTheme.typography.titleSmall,
            )
            // ffmpeg reports no percent, so the converting bar only moves.
            if (converting || percent == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { (percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (converting || percent == null) {
                        "Please wait"
                    } else {
                        "${percent.roundToInt()}%"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = if (etaSeconds != null) {
                        "${formatDuration(etaSeconds.toInt())} left"
                    } else {
                        "time left unknown"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TapHeight),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SavedLine(savedAs: String?) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Saved to your device as ${savedAs ?: "the download folder"}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}

/** One line of the quality menu, for example "1080p, about 94.2 MB". */
private fun qualityText(quality: Quality): String =
    if (quality.filesize != null) {
        "${quality.label}, about ${formatSize(quality.filesize)}"
    } else {
        quality.label
    }

/** The height when the stream has one, else the file type. */
private fun formatName(format: MediaFormat): String =
    format.height?.let { "${it}p" } ?: format.ext ?: format.formatId

private fun sizeText(bytes: Long?): String =
    if (bytes != null && bytes > 0) formatSize(bytes) else "size unknown"

/** Bytes to a short string, for example "94.2 MB". */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit += 1
    }
    if (unit == 0) return "${value.toInt()} B"
    return String.format(Locale.US, "%.1f %s", value, units[unit])
}

/** Seconds to a short string, for example "3m 42s". */
private fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (hours > 0 || minutes > 0) append("${minutes}m ")
        append("${rest}s")
    }
}

// ---- Previews -------------------------------------------------------------

private val previewFormats = listOf(
    MediaFormat(
        formatId = "137",
        ext = "mp4",
        height = 1080,
        fps = 30,
        videoCodec = "avc1.640028",
        audioCodec = MediaFormat.NONE,
        filesize = 95_000_000L,
        note = "1080p",
    ),
    MediaFormat(
        formatId = "136",
        ext = "mp4",
        height = 720,
        fps = 30,
        videoCodec = "avc1.4d401f",
        audioCodec = MediaFormat.NONE,
        filesize = 48_000_000L,
        note = "720p",
    ),
    MediaFormat(
        formatId = "18",
        ext = "mp4",
        height = 360,
        fps = 30,
        videoCodec = "avc1.42001E",
        audioCodec = "mp4a.40.2",
        filesize = null,
        note = "360p",
    ),
    MediaFormat(
        formatId = "140",
        ext = "m4a",
        height = null,
        fps = null,
        videoCodec = MediaFormat.NONE,
        audioCodec = "mp4a.40.2",
        filesize = 3_600_000L,
        note = "medium",
    ),
)

private val previewDetails = VideoDetails(
    id = "dBLqj8XT_c4",
    title = "A very long video title that must stay on two lines and then " +
        "stop with three dots rather than push the buttons away",
    durationSeconds = 222,
    thumbnailUrl = "https://example.invalid/thumb.jpg",
    formats = previewFormats,
)

@Composable
private fun PreviewFrame(state: UiState) {
    YTDLPMobileTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MainScreen(
                state = state,
                onUrlChanged = {},
                onCheck = {},
                onSelectHeight = {},
                onDownloadVideo = {},
                onDownloadAudio = {},
                onDownloadFormat = {},
                onToggleFormats = {},
                onCancel = {},
                onDismissError = {},
            )
        }
    }
}

@Preview(name = "Idle", showBackground = true)
@Composable
private fun IdlePreview() {
    PreviewFrame(UiState())
}

@Preview(name = "Ready", showBackground = true, heightDp = 900)
@Composable
private fun ReadyPreview() {
    PreviewFrame(
        UiState(
            url = "https://www.youtube.com/watch?v=dBLqj8XT_c4",
            phase = Phase.READY,
            details = previewDetails,
            selectedHeight = 1080,
        ),
    )
}

@Preview(name = "Ready with formats", showBackground = true, heightDp = 1100)
@Composable
private fun FormatsPreview() {
    PreviewFrame(
        UiState(
            url = "https://www.youtube.com/watch?v=dBLqj8XT_c4",
            phase = Phase.READY,
            details = previewDetails,
            showFormats = true,
        ),
    )
}

@Preview(name = "Downloading", showBackground = true, heightDp = 1000)
@Composable
private fun DownloadingPreview() {
    PreviewFrame(
        UiState(
            url = "https://www.youtube.com/watch?v=dBLqj8XT_c4",
            phase = Phase.DOWNLOADING,
            details = previewDetails,
            selectedHeight = 720,
            percent = 42f,
            etaSeconds = 95L,
        ),
    )
}

@Preview(name = "Converting", showBackground = true, heightDp = 1000)
@Composable
private fun ConvertingPreview() {
    PreviewFrame(
        UiState(
            url = "https://www.youtube.com/watch?v=dBLqj8XT_c4",
            phase = Phase.CONVERTING,
            details = previewDetails,
            percent = 100f,
        ),
    )
}

@Preview(name = "Done", showBackground = true, heightDp = 1000)
@Composable
private fun DonePreview() {
    PreviewFrame(
        UiState(
            url = "https://www.youtube.com/watch?v=dBLqj8XT_c4",
            phase = Phase.DONE,
            details = previewDetails,
            percent = 100f,
            savedAs = "A very long video title.mp4",
        ),
    )
}

@Preview(name = "Failed", showBackground = true)
@Composable
private fun FailedPreview() {
    PreviewFrame(
        UiState(
            url = "https://www.youtube.com/watch?v=dBLqj8XT_c4",
            phase = Phase.FAILED,
            error = "This video is private, so yt-dlp cannot read it.",
        ),
    )
}
