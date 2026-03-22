package com.cerealautomation.youtubedownloader

import com.cereal.sdk.ExecutionResult
import com.cereal.test.components.TestComponentProviderFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertIs

class TestYoutubeVideoDownloaderScript {

    @Test
    fun `execute succeeds with mocked extractor and downloader`() = runBlocking {
        val fakeStream = ResolvedStream(
            videoUrl = "https://example.com/video.mp4",
            audioUrl = null,
            qualityLabel = "720P",
            extension = "mp4",
            needsMux = false,
            isAudioOnly = false,
            heightPx = 720,
        )

        val mockExtractor = mockk<YouTubeExtractor> {
            every { getStreams(any()) } returns listOf(fakeStream)
            every { getTitle(any()) } returns "Test Video"
        }

        val mockDownloader = mockk<VideoDownloader> {
            every { download(any(), any()) } returns File(System.getProperty("java.io.tmpdir"), "Test_Video.mp4")
        }

        val script = YoutubeVideoDownloaderScript(
            extractor = mockExtractor,
            downloader = mockDownloader,
        )

        val configuration = mockk<YoutubeDownloaderConfiguration> {
            every { videoUrl() } returns "dQw4w9WgXcQ"
            every { quality() } returns DownloadQuality.Q720P
        }

        val componentProviderFactory = TestComponentProviderFactory()
        val provider = componentProviderFactory.create()

        script.onStart(configuration, provider)
        val result = script.execute(configuration, provider) { }

        assertIs<ExecutionResult.Success>(result)
        Unit
    }

    @Test
    fun `execute returns error for invalid video URL`() = runBlocking {
        val script = YoutubeVideoDownloaderScript()

        val configuration = mockk<YoutubeDownloaderConfiguration> {
            every { videoUrl() } returns "not-a-valid-url-or-id"
            every { quality() } returns DownloadQuality.BEST
        }

        val componentProviderFactory = TestComponentProviderFactory()

        script.onStart(configuration, componentProviderFactory.create())

        val result = script.execute(
            configuration,
            componentProviderFactory.create(),
        ) { }

        assertIs<ExecutionResult.Error>(result)
        Unit
    }
}
