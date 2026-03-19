import com.cereal.licensechecker.LicenseChecker
import com.cereal.licensechecker.LicenseState
import com.cereal.script.sample.DownloadQuality
import com.cereal.script.sample.ResolvedStream
import com.cereal.script.sample.VideoDownloader
import com.cereal.script.sample.YouTubeExtractor
import com.cereal.script.sample.YoutubeDownloaderConfiguration
import com.cereal.script.sample.YoutubeVideoDownloaderScript
import com.cereal.sdk.ExecutionResult
import com.cereal.test.components.TestComponentProviderFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
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
            every { download(any(), any()) } returns File("/tmp/Test Video.mp4")
        }

        val script = YoutubeVideoDownloaderScript(
            extractor = mockExtractor,
            downloader = mockDownloader,
        )

        mockkConstructor(LicenseChecker::class)
        coEvery { anyConstructed<LicenseChecker>().checkAccess() } returns LicenseState.Licensed

        val configuration = mockk<YoutubeDownloaderConfiguration> {
            every { videoUrl() } returns "dQw4w9WgXcQ"
            every { quality() } returns "720P"
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

        mockkConstructor(LicenseChecker::class)
        coEvery { anyConstructed<LicenseChecker>().checkAccess() } returns LicenseState.Licensed

        val configuration = mockk<YoutubeDownloaderConfiguration> {
            every { videoUrl() } returns "not-a-valid-url-or-id"
            every { quality() } returns "BEST"
        }

        val componentProviderFactory = TestComponentProviderFactory()

        // Force onStart to set isLicensed = true
        script.onStart(configuration, componentProviderFactory.create())

        val result = script.execute(
            configuration,
            componentProviderFactory.create(),
        ) { }

        assertIs<ExecutionResult.Error>(result)
        Unit
    }
}
