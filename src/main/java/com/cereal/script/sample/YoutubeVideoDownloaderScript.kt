package com.cereal.script.sample

import com.cereal.licensechecker.LicenseChecker
import com.cereal.licensechecker.LicenseState
import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.component.ComponentProvider

// TODO: Replace with the real script public key from the Cereal Developer Console.
private const val SCRIPT_PUBLIC_KEY = "-----BEGIN PUBLIC KEY-----\n" +
        "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAtL7rXEYD9WcQCGl8D9Ph\n" +
        "wj0WiPG/01+Y3rJyX5TRBfZLNE3hoLOPFDUzQOSy280e90Qv64Ux5plyUuts1Wbk\n" +
        "5vOH5q/TXEhdPixlwrVIAiMayIvV+t8mYCpOJBqaD+cvPQ1DYehUQ3hzax2XSd5O\n" +
        "K3N3r5iPJwtaLBLfSf8E5OnlCcADj8++3q52keTYkpJCrrJVwdJSs23oTq2aQEYj\n" +
        "WeQenq3Pl/J922kWqI8vZJiIb7kmKzcBdZR0zE39/d363dh/KU2c9v5DKFKG2HI6\n" +
        "I3eUkYGTUUqL+pLw9NtY4/tPmHN7FZXJ9rUvAaPk7oQzjSL2cJ1chmtcipUsZAy3\n" +
        "Fneh2HYlmAQpAc0V60DMzw9tQS2UQ5kQDGcC7h7xuAYHZT6jKhnuZon89Bek9qT+\n" +
        "ULgRMjuGTL4rpiMUabPj1IbGVZ6vTwYOjcltERh19MT8QchPo/UBB8W1CK4T3aLf\n" +
        "O3MHnGBeVTlhpBts57lAUGKP8RmGKLpmjL5lA4nw1B7BVzeJ2VuSy8Jhheq75IFp\n" +
        "kGoSrlqfxtA7SE8negMUEq6fca4J/Y5bABH6KHUrMiVaJGLa51Ert4qdOCvfJBlL\n" +
        "Ho/42AejYUJDi/P/fRiC99i6ObNPGXhQ9bz1Quz6F6VAzMjMmHo+OwQ5R2SHq2Yn\n" +
        "KmW5+hWaT3sqkxMw1a2JfTUCAwEAAQ==\n" +
        "-----END PUBLIC KEY-----\n"

class YoutubeVideoDownloaderScript : Script<YoutubeDownloaderConfiguration> {

    private var isLicensed = false
    private val extractor = YouTubeExtractor()
    private val downloader = VideoDownloader()

    override suspend fun onStart(
        configuration: YoutubeDownloaderConfiguration,
        provider: ComponentProvider,
    ): Boolean {
        val licenseChecker = LicenseChecker(
            "com.cereal-automation.youtubedownloader",
            SCRIPT_PUBLIC_KEY,
            provider.license(),
        )
        val result = licenseChecker.checkAccess()
        isLicensed = result is LicenseState.Licensed
        return result !is LicenseState.ErrorValidatingLicense
    }

    override suspend fun execute(
        configuration: YoutubeDownloaderConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult {
        if (!isLicensed) return ExecutionResult.Error("Unlicensed")

        val rawInput = configuration.videoUrl().trim()
        val quality = DownloadQuality.fromString(configuration.quality())

        statusUpdate("Parsing video URL…")
        val videoId = VideoIdParser.parse(rawInput)
            ?: return ExecutionResult.Error("Could not parse a YouTube video ID from: $rawInput")

        provider.logger().info("Resolved video ID: $videoId")
        statusUpdate("Fetching stream info…")

        val streams = try {
            extractor.getStreams(videoId)
        } catch (e: Exception) {
            return ExecutionResult.Error("Failed to fetch video streams: ${e.message}")
        }

        if (streams.isEmpty()) {
            return ExecutionResult.Error("No downloadable streams found for video ID: $videoId")
        }

        val stream = StreamSelector.select(streams, quality)
            ?: return ExecutionResult.Error("No streams available")

        if (stream.qualityLabel != quality.label) {
            provider.logger().info("Requested quality ${quality.label} unavailable; using ${stream.qualityLabel}")
        }

        provider.logger().info("Downloading stream: ${stream.qualityLabel} (${stream.extension}), needsMux=${stream.needsMux}")
        statusUpdate("Downloading…")

        val videoTitle = try {
            extractor.getTitle(videoId)
        } catch (e: Exception) {
            videoId
        }

        val outputFile = try {
            downloader.download(stream, videoTitle)
        } catch (e: Exception) {
            return ExecutionResult.Error("Download failed: ${e.message}")
        }

        provider.logger().info("Downloaded: ${outputFile.absolutePath}")
        return ExecutionResult.Success("Saved to ${outputFile.name}")
    }

    override suspend fun onFinish(
        configuration: YoutubeDownloaderConfiguration,
        provider: ComponentProvider,
    ) {
        // nothing to clean up
    }
}
