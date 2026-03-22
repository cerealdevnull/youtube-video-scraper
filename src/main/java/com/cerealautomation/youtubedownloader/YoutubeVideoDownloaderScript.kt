package com.cerealautomation.youtubedownloader

import com.cereal.sdk.ExecutionResult
import com.cereal.sdk.Script
import com.cereal.sdk.component.ComponentProvider

class YoutubeVideoDownloaderScript : Script<YoutubeDownloaderConfiguration> {

    internal var extractor: YouTubeExtractor = YouTubeExtractor()
    internal var downloader: VideoDownloader = VideoDownloader()

    override suspend fun onStart(
        configuration: YoutubeDownloaderConfiguration,
        provider: ComponentProvider,
    ): Boolean {
        return true
    }

    override suspend fun execute(
        configuration: YoutubeDownloaderConfiguration,
        provider: ComponentProvider,
        statusUpdate: suspend (message: String) -> Unit,
    ): ExecutionResult {
        val rawInput = configuration.videoUrl().trim()
        val quality = configuration.quality()

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

        if (stream.qualityLabel != quality.displayName) {
            provider.logger().info("Requested quality ${quality.displayName} unavailable; using ${stream.qualityLabel}")
        }

        provider.logger().info("Downloading stream: ${stream.qualityLabel} (${stream.extension}), needsMux=${stream.needsMux}")
        statusUpdate("Downloading…")

        val videoTitle = try {
            extractor.getTitle(videoId)
        } catch (e: Exception) {
            videoId
        }

        val outputFile = try {
            downloader.download(stream, videoTitle, statusUpdate)
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
