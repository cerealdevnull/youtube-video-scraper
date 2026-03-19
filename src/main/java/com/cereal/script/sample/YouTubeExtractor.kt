package com.cereal.script.sample

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor

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
                qualityLabel = if (audio.averageBitrate > 0) "audio-${audio.averageBitrate}kbps" else "audio-unknown",
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
            .filter { it.averageBitrate > 0 }
            .maxByOrNull { it.averageBitrate }
            ?: extractor.audioStreams.firstOrNull()

        for (video in extractor.videoOnlyStreams) {
            val audioContent = bestAudio?.content
            streams += ResolvedStream(
                videoUrl = video.content,
                audioUrl = audioContent,
                qualityLabel = video.resolution,
                extension = if (audioContent != null) "mp4" else video.format?.suffix ?: "webm",
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
        val body = request.dataToSend()
        val mediaType = request.headers()["Content-Type"]?.firstOrNull()
            ?.toMediaTypeOrNull()

        val okRequest = Request.Builder()
            .url(request.url())
            .apply {
                request.headers().forEach { (key, values) ->
                    if (key.equals("Content-Type", ignoreCase = true)) return@forEach
                    values.forEach { value -> addHeader(key, value) }
                }
                when (request.httpMethod()) {
                    "POST" -> post((body ?: ByteArray(0)).toRequestBody(mediaType))
                    "HEAD" -> head()
                    // GET is OkHttp default
                }
            }
            .build()

        return client.newCall(okRequest).execute().use { okResponse ->
            Response(
                okResponse.code,
                okResponse.message,
                okResponse.headers.toMultimap(),
                okResponse.body?.string(),
                okResponse.request.url.toString(),
            )
        }
    }
}
