package com.nesco.ytdlpmobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.nesco.ytdlpmobile.domain.model.DownloadMode
import com.nesco.ytdlpmobile.service.DownloadService
import com.nesco.ytdlpmobile.ui.RootScreen
import com.nesco.ytdlpmobile.ui.MainViewModel
import com.nesco.ytdlpmobile.ui.MainViewModelFactory
import com.nesco.ytdlpmobile.ui.Phase
import com.nesco.ytdlpmobile.ui.theme.YTDLPMobileTheme

/**
 * Shared text carries words around the link, so take the first link out of
 * it rather than trust the whole string.
 */
private val FIRST_URL = Regex("""https?://\S+""")

/** Punctuation a sentence leaves on the end of a link. */
private val TRAILING = charArrayOf('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')

fun firstUrlIn(text: String): String? =
    FIRST_URL.find(text)?.value?.trimEnd(*TRAILING)?.takeIf { it.isNotEmpty() }

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by lazy {
        val factory = MainViewModelFactory(application as App)
        ViewModelProvider(this, factory)[MainViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a cold start. A rotation keeps the ViewModel, and the
        // same link must not start a second check.
        if (savedInstanceState == null) takeSharedUrl(intent)
        setContent {
            YTDLPMobileTheme {
                val state by viewModel.state.collectAsState()
                AskForNotifications(state.phase)
                KeepServiceInStep(state.phase, state.percent)
                RootScreen(
                    state = state,
                    onUrlChanged = viewModel::onUrlChanged,
                    onCheck = viewModel::check,
                    onSelectHeight = viewModel::selectHeight,
                    onDownloadVideo = { viewModel.download(DownloadMode.VIDEO) },
                    onDownloadAudio = { viewModel.download(DownloadMode.AUDIO) },
                    onDownloadFormat = { id -> viewModel.download(DownloadMode.FORMAT, id) },
                    onToggleFormats = viewModel::toggleFormats,
                    onCancel = viewModel::cancel,
                    onDismissError = viewModel::dismissError,
                    onUpdateYtdlp = viewModel::updateYtdlp,
                    onNightly = viewModel::setNightly,
                    onBitrate = viewModel::setBitrate,
                    onDefaultHeight = viewModel::setDefaultHeight,
                    onCookiesChanged = viewModel::setCookies,
                )
            }
        }
    }

    /** The activity is singleTop, so a later share arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeSharedUrl(intent)
    }

    private fun takeSharedUrl(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        if (intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val url = firstUrlIn(text) ?: return
        viewModel.onUrlShared(url)
    }
}

/**
 * Ask for the notification permission once, when the first download starts.
 *
 * A refusal costs the notification and nothing else. The download still
 * runs, and the foreground service still holds the process up.
 */
@Composable
private fun AskForNotifications(phase: Phase) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        var asked by rememberSaveable { mutableStateOf(false) }
        val ask = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Nothing to do either way. */ }
        LaunchedEffect(phase, asked) {
            if (asked || phase != Phase.DOWNLOADING) return@LaunchedEffect
            asked = true
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Run the service while work runs, and stop it as soon as work ends. */
@Composable
private fun KeepServiceInStep(phase: Phase, percent: Float?) {
    val context = LocalContext.current
    val working = phase == Phase.DOWNLOADING || phase == Phase.CONVERTING
    // Whole percent only. A new notification for every fraction is work
    // the person cannot see.
    LaunchedEffect(working, phase, percent?.toInt()) {
        if (working) {
            DownloadService.showProgress(context, percent, phase == Phase.CONVERTING)
        } else {
            DownloadService.stop(context)
        }
    }
}
