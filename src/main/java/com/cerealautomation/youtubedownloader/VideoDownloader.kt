package com.cerealautomation.youtubedownloader

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class VideoDownloader(private val httpClient: OkHttpClient = OkHttpClient()) {

    private val downloadsDir: File = File(System.getProperty("user.home"), "Downloads")

    /**
     * Downloads the stream to ~/Downloads/<sanitizedTitle>.<ext>.
     * Returns the path of the output file.
     * Throws on network error or ffmpeg failure.
     */
    suspend fun download(
        stream: ResolvedStream,
        rawTitle: String,
        onProgress: suspend (String) -> Unit = {}
    ): File {
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        require(downloadsDir.exists() && downloadsDir.isDirectory) {
            "Downloads directory does not exist and could not be created: ${downloadsDir.absolutePath}"
        }

        val title = sanitizeTitle(rawTitle)
        val outputFile = uniqueFile(downloadsDir, title, stream.extension)

        if (stream.needsMux && stream.audioUrl != null) {
            val videoTmp = File.createTempFile("yt_video_", ".tmp", downloadsDir)
            val audioTmp = File.createTempFile("yt_audio_", ".tmp", downloadsDir)
            try {
                downloadToFile(stream.videoUrl, videoTmp, "Video", onProgress)
                downloadToFile(stream.audioUrl, audioTmp, "Audio", onProgress)
                onProgress("Merging high-quality video and audio...")
                mux(videoTmp, audioTmp, outputFile)
            } finally {
                videoTmp.delete()
                audioTmp.delete()
            }
        } else {
            downloadToFile(stream.videoUrl, outputFile, "File", onProgress)
        }

        return outputFile
    }

    private suspend fun downloadToFile(url: String, dest: File, label: String, onProgress: suspend (String) -> Unit) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw RuntimeException("Empty response body for $url")
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            
            if (totalBytes > 0) {
                onProgress("Downloading $label... 0%")
            }
            
            dest.outputStream().use { outputStream ->
                val buffer = ByteArray(8 * 1024)
                var bytesCopied: Long = 0
                var bytes = inputStream.read(buffer)
                var lastReportTime = System.currentTimeMillis()
                var lastReportedPercent = 0
                
                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    
                    if (totalBytes > 0) {
                        val percent = (bytesCopied * 100 / totalBytes).toInt()
                        if (percent >= lastReportedPercent + 5) {
                            lastReportedPercent = percent
                            onProgress("Downloading $label... $percent%")
                        }
                    } else {
                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 500) {
                            lastReportTime = now
                            val downloadedMb = bytesCopied / (1024 * 1024)
                            onProgress("Downloading $label... ${downloadedMb}MB")
                        }
                    }
                    
                    bytes = inputStream.read(buffer)
                }
                
                if (totalBytes > 0 && lastReportedPercent < 100) {
                    onProgress("Downloading $label... 100%")
                }
            }
        }
    }

    private suspend fun mux(video: File, audio: File, output: File) {
        val ffmpeg = findFfmpeg()
            ?: throw RuntimeException(
                "ffmpeg not found on PATH. Install it from https://ffmpeg.org and ensure it is on your system PATH."
            )

        val process = ProcessBuilder(
            ffmpeg,
            "-y",
            "-i", video.absolutePath,
            "-i", audio.absolutePath,
            "-c", "copy",
            output.absolutePath,
        )
            .redirectErrorStream(true)
            .start()

        // Drain ffmpeg's output on a background thread to prevent pipe buffer deadlock.
        // ffmpeg is chatty (progress per frame); without draining, it fills the OS pipe
        // buffer and blocks before the process can exit.
        val outputCapture = StringBuilder()
        val drainer = Thread {
            process.inputStream.bufferedReader().forEachLine { outputCapture.appendLine(it) }
        }
        drainer.start()

        val exitCode = process.waitFor()
        drainer.join()

        if (exitCode != 0) {
            output.delete()  // clean up partial output file on failure
            throw RuntimeException("ffmpeg failed (exit $exitCode):\n$outputCapture")
        }
    }

    private fun findFfmpeg(): String? {
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val candidates = if (isWin) listOf("ffmpeg.exe") else listOf("ffmpeg")

        // Check PATH entries
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        for (dir in pathDirs) {
            for (name in candidates) {
                val f = File(dir, name)
                if (f.canExecute()) return f.absolutePath
            }
        }
        
        // Fallback: well-known locations that may not be on PATH when launched as non-login process
        val fallbacks = if (isWin) {
            listOf(
                "C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\ffmpeg\\bin\\ffmpeg.exe",
                "C:\\ffmpeg\\ffmpeg.exe"
            )
        } else {
            listOf(
                "/opt/homebrew/bin/ffmpeg",  // Apple Silicon Homebrew
                "/usr/local/bin/ffmpeg",      // Intel Homebrew / standard Unix
                "/usr/bin/ffmpeg",            // Standard Linux
                "/snap/bin/ffmpeg"            // Ubuntu Snap
            )
        }
        
        for (path in fallbacks) {
            val f = File(path)
            if (f.canExecute()) return f.absolutePath
        }
        return null
    }

    internal fun sanitizeTitle(title: String): String {
        val illegal = Regex("""[/\\:*?"<>|]""")
        return title
            .replace(illegal, "_")
            .trim()
            .take(200)
            .ifBlank { "youtube_video" }
    }

    private fun uniqueFile(dir: File, base: String, ext: String): File {
        var candidate = File(dir, "$base.$ext")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($counter).$ext")
            counter++
        }
        return candidate
    }
}
