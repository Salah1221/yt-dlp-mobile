package com.nesco.ytdlpmobile

import android.app.Application
import com.nesco.ytdlpmobile.data.AndroidStorage
import com.nesco.ytdlpmobile.data.DownloadCoordinator
import com.nesco.ytdlpmobile.data.AndroidSettings
import com.nesco.ytdlpmobile.data.RealYtdlpClient
import com.nesco.ytdlpmobile.data.Settings
import com.nesco.ytdlpmobile.data.Storage
import com.nesco.ytdlpmobile.data.YtdlpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * The one place the client and the storage are built.
 *
 * yt-dlp keeps its unpacked files and its process table inside one library
 * instance, so the whole process must share one client.
 */
class App : Application() {

    /** Built on first use. The client unpacks itself then, not here. */
    val settings: Settings by lazy { AndroidSettings(this) }

    val client: YtdlpClient by lazy { RealYtdlpClient(this, settings) }

    val storage: Storage by lazy { AndroidStorage(this) }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Runs on the application scope, so a closed screen does not
     *  take the download with it. */
    val downloads: DownloadCoordinator by lazy {
        DownloadCoordinator(client, storage, background)
    }

    override fun onCreate() {
        super.onCreate()
        // This reads and deletes folders, so it stays off the main thread.
        // A folder nobody came back to in a week will not be resumed. It
        // only holds space.
        background.launch { runCatching { storage.sweepOlderThan(SWEEP_AGE_MILLIS) } }
    }

    private companion object {
        val SWEEP_AGE_MILLIS: Long = TimeUnit.DAYS.toMillis(7)
    }
}
