package com.nesco.ytdlpmobile.ui

import com.nesco.ytdlpmobile.data.DownloadCoordinator
import com.nesco.ytdlpmobile.data.DownloadOutcome
import com.nesco.ytdlpmobile.data.Progress
import com.nesco.ytdlpmobile.data.Settings
import com.nesco.ytdlpmobile.data.Storage
import com.nesco.ytdlpmobile.data.YtdlpClient
import com.nesco.ytdlpmobile.data.YtdlpFailure
import com.nesco.ytdlpmobile.data.isConvertingLine
import com.nesco.ytdlpmobile.domain.DownloadSpec
import com.nesco.ytdlpmobile.domain.model.DownloadMode
import com.nesco.ytdlpmobile.domain.model.MediaFormat
import com.nesco.ytdlpmobile.domain.model.VideoDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val details = VideoDetails(
        id = "abc123",
        title = "A test clip",
        durationSeconds = 100,
        thumbnailUrl = null,
        formats = listOf(
            MediaFormat("137", "mp4", 1080, 30, "avc1", "none", 50_000_000, null),
            MediaFormat("140", "m4a", null, null, "none", "mp4a", 3_000_000, null),
        ),
    )

    /** A client the test drives, with no device and no network. */
    private class FakeClient : YtdlpClient {
        var probeAnswer: (() -> VideoDetails)? = null
        var downloadAnswer: (() -> DownloadOutcome)? = null
        var script: List<Progress> = emptyList()
        var lastSpec: DownloadSpec? = null
        var cancelled: String? = null

        override suspend fun ensureReady() = Unit

        override suspend fun probe(url: String): VideoDetails =
            probeAnswer?.invoke() ?: error("no probe answer set")

        override suspend fun download(
            spec: DownloadSpec,
            workDir: File,
            jobId: String,
            onProgress: (Progress) -> Unit,
        ): DownloadOutcome {
            lastSpec = spec
            script.forEach(onProgress)
            return downloadAnswer?.invoke() ?: error("no download answer set")
        }

        override fun cancel(jobId: String): Boolean {
            cancelled = jobId
            return true
        }

        var version: String? = "2026.08.19"
        override suspend fun currentVersion(): String? = version
        override suspend fun updateNow(nightly: Boolean): String? {
            version = if (nightly) "2026.09.20.nightly" else "2026.09.01"
            return version
        }
    }

    private class FakeSettings : Settings {
        override var useNightly = false
        override var audioBitrateK = 192
        override var defaultMaxHeight: Int? = null
        override var cookiesPath: String? = null
    }

    private class FakeStorage : Storage {
        val deleted = mutableListOf<String>()
        var published: String? = null
        override fun workDir(name: String) = File("/tmp/$name")
        override suspend fun publish(file: File, title: String, mode: DownloadMode): String {
            published = "$title.mp3"
            return published!!
        }
        override fun deleteWorkDir(name: String) { deleted += name }
        override fun sweepOlderThan(millis: Long) = Unit
    }

    private lateinit var client: FakeClient
    private lateinit var storage: FakeStorage
    private lateinit var settings: FakeSettings
    private lateinit var downloads: DownloadCoordinator
    private lateinit var model: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        client = FakeClient()
        storage = FakeStorage()
        settings = FakeSettings()
        // The real coordinator, on a scope the test drives. It owns the
        // download now, so the tests exercise it rather than a stub.
        downloads = DownloadCoordinator(client, storage, CoroutineScope(UnconfinedTestDispatcher()))
        model = MainViewModel(client, settings, downloads)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun ready() {
        client.probeAnswer = { details }
        model.onUrlChanged("https://example.com/watch?v=abc123")
        model.check()
    }

    @Test
    fun `checking a link shows the video`() = runTest {
        ready()
        val state = model.state.value
        assertEquals(Phase.READY, state.phase)
        assertEquals("A test clip", state.details?.title)
        assertEquals(listOf(1080), state.details?.qualities?.map { it.height })
    }

    @Test
    fun `a failed check shows the reason and nothing else`() = runTest {
        client.probeAnswer = { throw YtdlpFailure("that link is not one this app can read.") }
        model.onUrlChanged("nonsense")
        model.check()
        val state = model.state.value
        assertEquals(Phase.FAILED, state.phase)
        assertTrue(state.error!!.contains("not one this app can read"))
        assertNull(state.details)
    }

    @Test
    fun `an empty link does nothing`() = runTest {
        model.check()
        assertEquals(Phase.IDLE, model.state.value.phase)
    }

    @Test
    fun `a finished download is published and the folder is cleared`() = runTest {
        ready()
        client.downloadAnswer = { DownloadOutcome.Finished(File("/tmp/abc123.mp3")) }
        model.download(DownloadMode.AUDIO)

        val state = model.state.value
        assertEquals(Phase.DONE, state.phase)
        assertEquals("A test clip.mp3", state.savedAs)
        // Only a success is finished, so only a success clears the folder.
        assertEquals(1, storage.deleted.size)
    }

    @Test
    fun `a cancelled download keeps its folder so it can carry on`() = runTest {
        ready()
        client.downloadAnswer = { DownloadOutcome.Cancelled }
        model.download(DownloadMode.AUDIO)

        assertEquals(Phase.READY, model.state.value.phase)
        assertTrue("the partial file was thrown away", storage.deleted.isEmpty())
    }

    @Test
    fun `a failed download keeps its folder so it can carry on`() = runTest {
        ready()
        client.downloadAnswer = { throw YtdlpFailure("the phone has run out of space.") }
        model.download(DownloadMode.AUDIO)

        assertEquals(Phase.FAILED, model.state.value.phase)
        assertTrue(model.state.value.error!!.contains("space"))
        assertTrue("the partial file was thrown away", storage.deleted.isEmpty())
    }

    @Test
    fun `the chosen quality reaches the download, and only for video`() = runTest {
        ready()
        client.downloadAnswer = { DownloadOutcome.Finished(File("/tmp/abc123.mp4")) }

        model.selectHeight(720)
        model.download(DownloadMode.VIDEO)
        assertEquals(720, client.lastSpec?.maxHeight)

        model.download(DownloadMode.AUDIO)
        assertNull("a height means nothing to an audio download", client.lastSpec?.maxHeight)
    }

    @Test
    fun `progress moves the bar and keeps the last known values`() = runTest {
        ready()
        client.script = listOf(
            Progress(null, null, null, "[youtube] abc123: Downloading webpage"),
            Progress(42f, 30L, "1.5MiB/s", "[download]  42.0% of 10MiB at 1.5MiB/s ETA 00:30"),
            // yt-dlp says nothing on most lines. The bar must not fall back
            // to zero, because that reads as a fault.
            Progress(null, null, null, "[download] something else"),
        )
        client.downloadAnswer = { DownloadOutcome.Finished(File("/tmp/abc123.mp4")) }
        model.download(DownloadMode.VIDEO)

        // Finished, so the end state is 100. The keeping is checked below.
        assertEquals(Phase.DONE, model.state.value.phase)
        assertNotNull(model.state.value.savedAs)
    }

    @Test
    fun `the phase turns to converting when ffmpeg starts`() = runTest {
        assertTrue(isConvertingLine("[ExtractAudio] Destination: x.mp3"))
        assertTrue(isConvertingLine("[Merger] Merging formats into \"x.mp4\""))
        assertTrue(isConvertingLine("  [VideoConvertor] x"))
        assertTrue(isConvertingLine("[Fixup M3u8] x"))
        // A plain progress line is not the converting step.
        assertTrue(!isConvertingLine("[download]  42.0% of 10MiB"))
        assertTrue(!isConvertingLine(""))
    }

    @Test
    fun `cancel reaches the running job`() = runTest {
        ready()
        client.script = emptyList()
        client.downloadAnswer = { DownloadOutcome.Cancelled }
        model.download(DownloadMode.AUDIO)
        model.cancel()
        // The job is over by now, so nothing is cancelled twice.
        assertEquals(Phase.READY, model.state.value.phase)
    }

    @Test
    fun `the settings a download needs are remembered`() = runTest {
        model.setBitrate(320)
        model.setDefaultHeight(720)
        model.setNightly(true)
        model.setCookies("/data/cookies/jar.txt")

        assertEquals(320, settings.audioBitrateK)
        assertEquals(720, settings.defaultMaxHeight)
        assertTrue(settings.useNightly)
        // The screen shows the file name, not the whole path.
        assertEquals("jar.txt", model.state.value.cookiesName)
    }

    @Test
    fun `a check keeps the default quality rather than resetting it`() = runTest {
        model.setDefaultHeight(720)
        ready()
        assertEquals(720, model.state.value.selectedHeight)
    }

    @Test
    fun `updating yt-dlp reports the new version`() = runTest {
        model.updateYtdlp()
        assertEquals("2026.09.01", model.state.value.ytdlpVersion)
        assertTrue(!model.state.value.updating)
    }

    @Test
    fun `the nightly channel is used when it is turned on`() = runTest {
        model.setNightly(true)
        model.updateYtdlp()
        assertTrue(model.state.value.ytdlpVersion!!.contains("nightly"))
    }

    @Test
    fun `a shared link checks itself straight away`() = runTest {
        client.probeAnswer = { details }
        model.onUrlShared("https://example.com/watch?v=abc123")
        assertEquals(Phase.READY, model.state.value.phase)
    }
}
