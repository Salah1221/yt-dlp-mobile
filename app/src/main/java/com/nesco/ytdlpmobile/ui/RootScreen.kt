package com.nesco.ytdlpmobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.nesco.ytdlpmobile.R

private const val TAB_DOWNLOAD = 0
private const val TAB_SETTINGS = 1

/**
 * Holds the two destinations. The tab survives a rotation, so a download
 * in progress does not jump back to the other screen.
 */
@Composable
fun RootScreen(
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
    onUpdateYtdlp: () -> Unit,
    onNightly: (Boolean) -> Unit,
    onBitrate: (Int) -> Unit,
    onDefaultHeight: (Int?) -> Unit,
    onCookiesChanged: (String?) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_DOWNLOAD) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == TAB_DOWNLOAD,
                    onClick = { tab = TAB_DOWNLOAD },
                    icon = {
                        Icon(painterResource(R.drawable.ic_nav_download), contentDescription = null)
                    },
                    label = { Text("Download") },
                )
                NavigationBarItem(
                    selected = tab == TAB_SETTINGS,
                    onClick = { tab = TAB_SETTINGS },
                    icon = {
                        Icon(painterResource(R.drawable.ic_nav_settings), contentDescription = null)
                    },
                    label = { Text("Settings") },
                )
            }
        },
    ) { innerPadding ->
        // Each screen pads for the status bar itself. The bar at the
        // bottom is what this padding covers.
        Box(modifier = Modifier.fillMaxSize().padding(bottom = innerPadding.calculateBottomPadding())) {
            when (tab) {
                TAB_SETTINGS -> SettingsScreen(
                    state = state,
                    onUpdateYtdlp = onUpdateYtdlp,
                    onNightly = onNightly,
                    onBitrate = onBitrate,
                    onDefaultHeight = onDefaultHeight,
                    onCookiesChanged = onCookiesChanged,
                )

                else -> MainScreen(
                    state = state,
                    onUrlChanged = onUrlChanged,
                    onCheck = onCheck,
                    onSelectHeight = onSelectHeight,
                    onDownloadVideo = onDownloadVideo,
                    onDownloadAudio = onDownloadAudio,
                    onDownloadFormat = onDownloadFormat,
                    onToggleFormats = onToggleFormats,
                    onCancel = onCancel,
                    onDismissError = onDismissError,
                )
            }
        }
    }
}
