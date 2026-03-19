package com.cereal.script.sample

import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.VideoStream

class YouTubeExtractor {

    private val httpClient = OkHttpClient()

    init {
        NewPipe.init(NewPipeDownloader(httpClient))
    }

    /**
     * Returns all available streams for a YouTube video ID.
     * Throws if the video is unavailable or the ID is invalid.
     */
    fun getStreams(videoId: String): List<ResolvedStream> {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val extractor: StreamExtractor = ServiceList.YouTube
            .getStreamExtractor(url)
            .also { it.fetchPage() }

        val streams = mutableListOf<ResolvedStream>()

        // --- Audio-only streams ---
        for (audio in extractor.audioStreams) {
            streams += ResolvedStream(
                videoUrl = audio.content,
                audioUrl = null,
                qualityLabel = "audio-${audio.averageBitrate}kbps",
                extension = audio.format?.suffix ?: "m4a",
                needsMux = false,
                isAudioOnly = true,
                heightPx = 0,
            )
        }

        // --- Combined (muxed) video+audio streams ---
        for (video in extractor.videoStreams) {
            streams += ResolvedStream(
                videoUrl = video.content,
                audioUrl = null,
                qualityLabel = video.resolution,
                extension = video.format?.suffix ?: "mp4",
                needsMux = false,
                isAudioOnly = false,
                heightPx = video.height,
            )
        }

        // --- Adaptive video-only streams (paired with best audio) ---
        val bestAudio: AudioStream? = extractor.audioStreams
            .maxByOrNull { it.averageBitrate }

        for (video in extractor.videoOnlyStreams) {
            val audioContent = bestAudio?.content
            streams += ResolvedStream(
                videoUrl = video.content,
                audioUrl = audioContent,
                qualityLabel = video.resolution,
                extension = "mp4",    // ffmpeg output will always be mp4
                needsMux = audioContent != null,
                isAudioOnly = false,
                heightPx = video.height,
            )
        }

        return streams
    }

    /**
     * Returns the title of the video for use as the output filename.
     * Makes a separate network call — acceptable for single-video use.
     */
    fun getTitle(videoId: String): String {
        val url = "https://www.youtube.com/watch?v=$videoId"
        return ServiceList.YouTube
            .getStreamExtractor(url)
            .also { it.fetchPage() }
            .name
    }
}

/** Thin OkHttp-backed Downloader for NewPipeExtractor */
private class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): Response {
        val okRequest = Request.Builder()
            .url(request.url())
            .apply {
                request.headers().forEach { (key, values) ->
                    values.forEach { value -> addHeader(key, value) }
                }
            }
            .build()

        val okResponse = client.newCall(okRequest).execute()

        return Response(
            okResponse.code,
            okResponse.message,
            okResponse.headers.toMultimap(),
            okResponse.body?.string(),
            okResponse.request.url.toString(),
        )
    }
}
