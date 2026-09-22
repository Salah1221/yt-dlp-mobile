package com.nesco.ytdlpmobile.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nesco.ytdlpmobile.data.Settings
import com.nesco.ytdlpmobile.ui.theme.YTDLPMobileTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    state: UiState,
    onUpdateYtdlp: () -> Unit,
    onNightly: (Boolean) -> Unit,
    onBitrate: (Int) -> Unit,
    onDefaultHeight: (Int?) -> Unit,
    onCookiesChanged: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The picked file is copied into app storage, because yt-dlp is a
    // native process and cannot open a content URI.
    val pickCookies = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(context.filesDir, "cookies.txt")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    }
                    target.absolutePath
                }.getOrNull()
            }
            onCookiesChanged(path)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Section("Downloader") {
            LabelledRow("yt-dlp version", state.ytdlpVersion ?: "not updated yet")
            Text(
                "The copy inside the app goes stale within weeks, because " +
                    "video sites change. Updating keeps downloads working.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onUpdateYtdlp,
                    enabled = !state.updating,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(if (state.updating) "Updating" else "Update now")
                }
                if (state.updating) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 24.dp))
                }
            }
            state.updateNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            SwitchRow(
                title = "Nightly builds",
                checked = state.nightly,
                onChange = onNightly,
            )
        }

        Section("Downloads") {
            ChoiceRow(
                label = "Default quality",
                shown = state.defaultMaxHeight?.let { "${it}p" } ?: "Best available",
                options = Settings.HEIGHTS,
                render = { it?.let { h -> "${h}p" } ?: "Best available" },
                onPick = onDefaultHeight,
            )
            ChoiceRow(
                label = "MP3 quality",
                shown = "${state.audioBitrateK} kbit/s",
                options = Settings.BITRATES,
                render = { "$it kbit/s" },
                onPick = onBitrate,
            )
        }

        Section("Cookies") {
            LabelledRow("File", state.cookiesName ?: "none")
            Text(
                "A cookies.txt exported from a signed in browser is the one " +
                    "thing that answers the robot check on a phone. The file " +
                    "is a live credential, so use a throwaway account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { pickCookies.launch(arrayOf("text/*", "application/octet-stream")) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Choose file")
                }
                if (state.cookiesName != null) {
                    TextButton(
                        onClick = { onCookiesChanged(null) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun LabelledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The weight is what stops long text pushing the switch off the card.
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 16.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    shown: String,
    options: List<T>,
    render: (T) -> String,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Column {
            OutlinedButton(
                onClick = { open = true },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(shown)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(render(option)) },
                        onClick = {
                            open = false
                            onPick(option)
                        },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    YTDLPMobileTheme {
        SettingsScreen(
            state = UiState(
                ytdlpVersion = "2026.09.01",
                audioBitrateK = 192,
                defaultMaxHeight = 1080,
                cookiesName = "cookies.txt",
            ),
            onUpdateYtdlp = {},
            onNightly = {},
            onBitrate = {},
            onDefaultHeight = {},
            onCookiesChanged = {},
        )
    }
}
