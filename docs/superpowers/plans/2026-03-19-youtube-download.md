# YouTube Video Downloader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the sample Cereal script template with a working YouTube video downloader that resolves stream URLs via NewPipeExtractor, downloads via OkHttp, and muxes with ffmpeg when needed, saving to `~/Downloads`.

**Architecture:** `YoutubeVideoDownloaderScript` (Cereal entry point) orchestrates `YouTubeExtractor` (NewPipeExtractor wrapper) and `VideoDownloader` (OkHttp + ffmpeg). Quality selection and mux decision are driven by stream metadata, not hardcoded quality rules.

**Tech Stack:** Kotlin/JVM 17, Gradle, NewPipeExtractor v0.26.0 (via JitPack), OkHttp 4.12.0, ffmpeg (system), MockK, Cereal SDK 1.9.0

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `gradle/libs.versions.toml` | Add NewPipeExtractor and OkHttp version entries |
| Modify | `build.gradle.kts` | Add JitPack repo, NewPipeExtractor and OkHttp dependencies |
| Rename/replace | `src/main/java/com/cereal/script/sample/SampleConfiguration.kt` → `YoutubeDownloaderConfiguration.kt` | Configuration interface: `videoUrl()`, `quality()` |
| Create | `src/main/java/com/cereal/script/sample/DownloadQuality.kt` | Enum: `BEST`, `Q1080P`, `Q720P`, `Q480P`, `Q360P`, `AUDIO_ONLY` + `fromString()` |
| Create | `src/main/java/com/cereal/script/sample/ResolvedStream.kt` | Data class: `videoUrl`, `audioUrl?`, `qualityLabel`, `extension`, `needsMux` |
| Create | `src/main/java/com/cereal/script/sample/VideoIdParser.kt` | Parses full YouTube URLs and raw IDs → video ID string |
| Create | `src/main/java/com/cereal/script/sample/YouTubeExtractor.kt` | Wraps NewPipeExtractor; returns `List<ResolvedStream>` for a video ID |
| Create | `src/main/java/com/cereal/script/sample/VideoDownloader.kt` | OkHttp download + ffmpeg mux; writes to `~/Downloads` |
| Rename/replace | `src/main/java/com/cereal/script/sample/SampleScript.kt` → `YoutubeVideoDownloaderScript.kt` | Script entry point: orchestrates the flow |
| Modify | `src/main/resources/manifest.json` | Update `name`, `package_name`, `script` class reference |
| Replace | `src/test/java/TestSampleScript.kt` → `TestYoutubeVideoDownloaderScript.kt` | Integration-style test using Cereal test harness |
| Create | `src/test/java/VideoIdParserTest.kt` | Unit tests for URL/ID parsing |

---

## Task 1: Add Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add version entries to `libs.versions.toml`**

Add to `[versions]`:
```toml
newpipe-extractor = "v0.26.0"
okhttp = "4.12.0"
```

Add to `[libraries]`:
```toml
newpipe-extractor = { module = "com.github.TeamNewPipe:NewPipeExtractor", version.ref = "newpipe-extractor" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
```

- [ ] **Step 2: Add JitPack repository and dependencies to `build.gradle.kts`**

Add JitPack to the `allprojects.repositories` block (after the existing `mavenCentral()` and cereal maven entries):
```kotlin
maven {
    url = uri("https://jitpack.io")
}
```

Add to the `dependencies` block:
```kotlin
implementation(libs.newpipe.extractor)
implementation(libs.okhttp)
```

- [ ] **Step 3: Sync and verify build compiles**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL (no errors)

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts
git commit -m "build: add NewPipeExtractor and OkHttp dependencies"
```

---

## Task 2: `DownloadQuality` Enum

**Files:**
- Create: `src/main/java/com/cereal/script/sample/DownloadQuality.kt`

- [ ] **Step 1: Create the enum**

```kotlin
package com.cereal.script.sample

enum class DownloadQuality(val label: String) {
    BEST("BEST"),
    Q1080P("1080P"),
    Q720P("720P"),
    Q480P("480P"),
    Q360P("360P"),
    AUDIO_ONLY("AUDIO_ONLY");

    companion object {
        fun fromString(value: String): DownloadQuality =
            entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) }
                ?: BEST
    }
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cereal/script/sample/DownloadQuality.kt
git commit -m "feat: add DownloadQuality enum"
```

---

## Task 3: `ResolvedStream` Data Class

**Files:**
- Create: `src/main/java/com/cereal/script/sample/ResolvedStream.kt`

- [ ] **Step 1: Create the data class**

```kotlin
package com.cereal.script.sample

data class ResolvedStream(
    val videoUrl: String,
    val audioUrl: String?,
    val qualityLabel: String,
    val extension: String,
    val needsMux: Boolean,
    val isAudioOnly: Boolean,
    val heightPx: Int,          // 0 for audio-only streams
)
```

- [ ] **Step 2: Compile check**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cereal/script/sample/ResolvedStream.kt
git commit -m "feat: add ResolvedStream data class"
```

---

## Task 4: `VideoIdParser`

**Files:**
- Create: `src/main/java/com/cereal/script/sample/VideoIdParser.kt`
- Test: `src/test/java/VideoIdParserTest.kt`

- [ ] **Step 1: Write the failing tests first**

Create `src/test/java/VideoIdParserTest.kt`:

```kotlin
import com.cereal.script.sample.VideoIdParser
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoIdParserTest {

    @Test
    fun `parses standard watch URL`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `parses short youtu_be URL`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `parses short youtu_be URL with query params`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("https://youtu.be/dQw4w9WgXcQ?si=abc123"))
    }

    @Test
    fun `parses raw video ID`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("dQw4w9WgXcQ"))
    }

    @Test
    fun `returns null for empty string`() {
        assertNull(VideoIdParser.parse(""))
    }

    @Test
    fun `returns null for unrecognised URL`() {
        assertNull(VideoIdParser.parse("https://example.com/video/123"))
    }

    @Test
    fun `parses watch URL with extra query params`() {
        assertEquals("dQw4w9WgXcQ", VideoIdParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s&list=PL123"))
    }
}
```

- [ ] **Step 2: Run tests — they must fail**

```bash
./gradlew test --tests "VideoIdParserTest"
```

Expected: FAIL — `VideoIdParser` does not exist yet

- [ ] **Step 3: Implement `VideoIdParser`**

Create `src/main/java/com/cereal/script/sample/VideoIdParser.kt`:

```kotlin
package com.cereal.script.sample

import java.net.URI

object VideoIdParser {

    // YouTube video IDs are always exactly 11 characters
    private val RAW_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    fun parse(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        return tryParseAsUrl(trimmed) ?: tryParseAsRawId(trimmed)
    }

    private fun tryParseAsUrl(input: String): String? {
        return try {
            val uri = URI(input)
            when {
                // https://youtu.be/<id>
                uri.host?.endsWith("youtu.be") == true -> {
                    uri.path?.removePrefix("/")?.substringBefore("/")?.takeIf { it.isNotBlank() }
                }
                // https://www.youtube.com/watch?v=<id>
                uri.host?.contains("youtube.com") == true -> {
                    uri.query
                        ?.split("&")
                        ?.firstOrNull { it.startsWith("v=") }
                        ?.removePrefix("v=")
                        ?.takeIf { it.isNotBlank() }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun tryParseAsRawId(input: String): String? =
        if (RAW_ID_REGEX.matches(input)) input else null
}
```

- [ ] **Step 4: Run tests — they must all pass**

```bash
./gradlew test --tests "VideoIdParserTest"
```

Expected: all 7 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cereal/script/sample/VideoIdParser.kt src/test/java/VideoIdParserTest.kt
git commit -m "feat: add VideoIdParser with full test coverage"
```

---

## Task 5: `YouTubeExtractor`

**Files:**
- Create: `src/main/java/com/cereal/script/sample/YouTubeExtractor.kt`

Note: NewPipeExtractor requires a `Downloader` implementation to make HTTP requests. The library ships a no-op `RecordingDownloader` for testing; for production use we provide a thin OkHttp-backed implementation inline.

- [ ] **Step 1: Create `YouTubeExtractor`**

Create `src/main/java/com/cereal/script/sample/YouTubeExtractor.kt`:

```kotlin
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
```

- [ ] **Step 2: Compile check**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cereal/script/sample/YouTubeExtractor.kt
git commit -m "feat: add YouTubeExtractor wrapping NewPipeExtractor"
```

---

## Task 6: Stream Selection Logic

This logic lives inside `YoutubeVideoDownloaderScript` but is extracted here as a testable pure function to keep the script entry point thin.

**Files:**
- Create: `src/main/java/com/cereal/script/sample/StreamSelector.kt`

- [ ] **Step 1: Create `StreamSelector`**

Create `src/main/java/com/cereal/script/sample/StreamSelector.kt`:

```kotlin
package com.cereal.script.sample

object StreamSelector {

    /**
     * Select the best matching stream for the requested quality.
     * Returns null if no streams are available.
     */
    fun select(streams: List<ResolvedStream>, quality: DownloadQuality): ResolvedStream? {
        if (streams.isEmpty()) return null

        return when (quality) {
            DownloadQuality.AUDIO_ONLY -> {
                streams
                    .filter { it.isAudioOnly }
                    .maxByOrNull { parseBitrateKbps(it.qualityLabel) }
                    ?: streams.first()
            }
            DownloadQuality.BEST -> {
                streams
                    .filter { !it.isAudioOnly }
                    .maxByOrNull { it.heightPx }
                    ?: streams.filter { it.isAudioOnly }.maxByOrNull { parseBitrateKbps(it.qualityLabel) }
            }
            else -> {
                val targetHeight = qualityToHeight(quality)
                val videoStreams = streams.filter { !it.isAudioOnly }

                // Exact match first
                videoStreams.firstOrNull { it.heightPx == targetHeight }
                    // Then next-lower available quality
                    ?: videoStreams
                        .filter { it.heightPx < targetHeight }
                        .maxByOrNull { it.heightPx }
                    // Then next-higher (last resort)
                    ?: videoStreams.minByOrNull { it.heightPx }
            }
        }
    }

    private fun qualityToHeight(quality: DownloadQuality): Int = when (quality) {
        DownloadQuality.Q1080P -> 1080
        DownloadQuality.Q720P  -> 720
        DownloadQuality.Q480P  -> 480
        DownloadQuality.Q360P  -> 360
        else -> 0
    }

    // Audio stream labels are "audio-<bitrate>kbps"; extract the numeric bitrate for ranking.
    private fun parseBitrateKbps(label: String): Int =
        Regex("(\\d+)kbps").find(label)?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cereal/script/sample/StreamSelector.kt
git commit -m "feat: add StreamSelector for quality-based stream picking"
```

---

## Task 7: `VideoDownloader`

**Files:**
- Create: `src/main/java/com/cereal/script/sample/VideoDownloader.kt`

- [ ] **Step 1: Create `VideoDownloader`**

Create `src/main/java/com/cereal/script/sample/VideoDownloader.kt`:

```kotlin
package com.cereal.script.sample

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

class VideoDownloader(private val httpClient: OkHttpClient = OkHttpClient()) {

    private val downloadsDir: File = File(System.getProperty("user.home"), "Downloads")

    /**
     * Downloads the stream to ~/Downloads/<sanitizedTitle>.<ext>.
     * Returns the path of the output file.
     * Throws on network error or ffmpeg failure.
     */
    fun download(stream: ResolvedStream, rawTitle: String): File {
        val title = sanitizeTitle(rawTitle)
        val outputFile = uniqueFile(downloadsDir, title, stream.extension)

        if (stream.needsMux && stream.audioUrl != null) {
            val videoTmp = File(downloadsDir, "$title.video.tmp")
            val audioTmp = File(downloadsDir, "$title.audio.tmp")
            try {
                downloadToFile(stream.videoUrl, videoTmp)
                downloadToFile(stream.audioUrl, audioTmp)
                mux(videoTmp, audioTmp, outputFile)
            } finally {
                videoTmp.delete()
                audioTmp.delete()
            }
        } else {
            downloadToFile(stream.videoUrl, outputFile)
        }

        return outputFile
    }

    private fun downloadToFile(url: String, dest: File) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Download failed: HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw RuntimeException("Empty response body for $url")
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    private fun mux(video: File, audio: File, output: File) {
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

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val stderr = process.inputStream.bufferedReader().readText()
            throw RuntimeException("ffmpeg failed (exit $exitCode):\n$stderr")
        }
    }

    private fun findFfmpeg(): String? {
        val candidates = if (System.getProperty("os.name").lowercase().contains("win"))
            listOf("ffmpeg.exe") else listOf("ffmpeg")

        // Check PATH entries
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator) ?: emptyList()
        for (dir in pathDirs) {
            for (name in candidates) {
                val f = File(dir, name)
                if (f.canExecute()) return f.absolutePath
            }
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
```

- [ ] **Step 2: Compile check**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/cereal/script/sample/VideoDownloader.kt
git commit -m "feat: add VideoDownloader with OkHttp download and ffmpeg mux"
```

---

## Task 8: `YoutubeDownloaderConfiguration`

**Files:**
- Create: `src/main/java/com/cereal/script/sample/YoutubeDownloaderConfiguration.kt`
- Delete: `src/main/java/com/cereal/script/sample/SampleConfiguration.kt`

- [ ] **Step 1: Create the configuration interface**

Create `src/main/java/com/cereal/script/sample/YoutubeDownloaderConfiguration.kt`:

```kotlin
package com.cereal.script.sample

import com.cereal.sdk.ScriptConfiguration
import com.cereal.sdk.ScriptConfigurationItem

interface YoutubeDownloaderConfiguration : ScriptConfiguration {

    @ScriptConfigurationItem(
        keyName = "VideoUrl",
        name = "YouTube URL or Video ID",
        description = "Paste a full YouTube URL (e.g. https://www.youtube.com/watch?v=xxxxx) or a raw 11-character video ID."
    )
    fun videoUrl(): String

    @ScriptConfigurationItem(
        keyName = "Quality",
        name = "Quality",
        description = "Desired video quality. Options: BEST, 1080P, 720P, 480P, 360P, AUDIO_ONLY. Falls back to next-lower quality if unavailable. BEST and 1080P require ffmpeg."
    )
    fun quality(): String
}
```

- [ ] **Step 2: Delete the old sample configuration**

```bash
rm src/main/java/com/cereal/script/sample/SampleConfiguration.kt
```

- [ ] **Step 3: Compile check**

```bash
./gradlew compileKotlin
```

Expected: **BUILD FAILS** — `SampleScript.kt` still references `SampleConfiguration` which has been deleted. This is expected and will be fixed in Task 9 when `SampleScript.kt` is replaced.

- [ ] **Step 4: Stage changes (do NOT commit yet — commit together with Task 9)**

```bash
git add src/main/java/com/cereal/script/sample/YoutubeDownloaderConfiguration.kt
git rm src/main/java/com/cereal/script/sample/SampleConfiguration.kt
```

> **Note:** Do not commit here. The build is broken at this point (SampleScript still references the deleted SampleConfiguration). Stage the files and proceed directly to Task 9. Both tasks are committed together at the end of Task 9 to avoid landing a broken build.

---

## Task 9: `YoutubeVideoDownloaderScript` (entry point)

**Files:**
- Create: `src/main/java/com/cereal/script/sample/YoutubeVideoDownloaderScript.kt`
- Delete: `src/main/java/com/cereal/script/sample/SampleScript.kt`

- [ ] **Step 1: Create the script entry point**

Create `src/main/java/com/cereal/script/sample/YoutubeVideoDownloaderScript.kt`:

```kotlin
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
```

Note: `getTitle` is already implemented in `YouTubeExtractor` as part of Task 5. No additional changes to `YouTubeExtractor.kt` are needed here.

- [ ] **Step 2: Delete old `SampleScript.kt`**

```bash
rm src/main/java/com/cereal/script/sample/SampleScript.kt
```

- [ ] **Step 3: Compile check**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit Tasks 8 and 9 together**

```bash
git add src/main/java/com/cereal/script/sample/YoutubeVideoDownloaderScript.kt \
        src/main/java/com/cereal/script/sample/YouTubeExtractor.kt
git rm src/main/java/com/cereal/script/sample/SampleScript.kt
git commit -m "feat: add configuration + script entry point, remove sample files"
```

---

## Task 10: Update `manifest.json`

**Files:**
- Modify: `src/main/resources/manifest.json`

- [ ] **Step 1: Update the manifest**

Replace the contents of `src/main/resources/manifest.json`:

```json
{
  "package_name": "com.cereal-automation.youtubedownloader",
  "name": "YouTube Video Downloader",
  "version_code": 1,
  "script": "com.cereal.script.sample.YoutubeVideoDownloaderScript",
  "instructions": "Paste a YouTube URL or video ID, select quality, and press Start. Files are saved to your Downloads folder. HD quality (1080P and BEST) requires ffmpeg to be installed."
}
```

- [ ] **Step 2: Full build check**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL (tests may fail at this point — that's fixed in Task 11)

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/manifest.json
git commit -m "chore: update manifest for YouTube Video Downloader"
```

---

## Task 11: Update Tests

**Files:**
- Replace: `src/test/java/TestSampleScript.kt` → `src/test/java/TestYoutubeVideoDownloaderScript.kt`

- [ ] **Step 1: Delete old test file**

```bash
rm src/test/java/TestSampleScript.kt
```

- [ ] **Step 2: Create the new integration-style test**

Create `src/test/java/TestYoutubeVideoDownloaderScript.kt`:

```kotlin
import com.cereal.licensechecker.LicenseChecker
import com.cereal.licensechecker.LicenseState
import com.cereal.script.sample.DownloadQuality
import com.cereal.script.sample.ResolvedStream
import com.cereal.script.sample.VideoDownloader
import com.cereal.script.sample.YouTubeExtractor
import com.cereal.script.sample.YoutubeDownloaderConfiguration
import com.cereal.script.sample.YoutubeVideoDownloaderScript
import com.cereal.sdk.ExecutionResult
import com.cereal.test.TestScriptRunner
import com.cereal.test.components.TestComponentProviderFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.spyk
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
            qualityLabel = "720p",
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

        val script = spyk(YoutubeVideoDownloaderScript()) {
            // Inject mocked collaborators via reflection for testability
            val extractorField = YoutubeVideoDownloaderScript::class.java.getDeclaredField("extractor")
            extractorField.isAccessible = true
            extractorField.set(this, mockExtractor)

            val downloaderField = YoutubeVideoDownloaderScript::class.java.getDeclaredField("downloader")
            downloaderField.isAccessible = true
            downloaderField.set(this, mockDownloader)
        }

        mockkConstructor(LicenseChecker::class)
        coEvery { anyConstructed<LicenseChecker>().checkAccess() } returns LicenseState.Licensed

        val configuration = mockk<YoutubeDownloaderConfiguration> {
            every { videoUrl() } returns "dQw4w9WgXcQ"
            every { quality() } returns "720P"
        }

        val scriptRunner = TestScriptRunner(script)
        val componentProviderFactory = TestComponentProviderFactory()

        scriptRunner.run(configuration, componentProviderFactory)
        // If no exception is thrown, the test passes.
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
        val scriptRunner = TestScriptRunner(script)

        // Force onStart to set isLicensed = true
        script.onStart(configuration, componentProviderFactory.create(configuration))

        val result = script.execute(
            configuration,
            componentProviderFactory.create(configuration),
        ) { }

        assertIs<ExecutionResult.Error>(result)
    }
}
```

- [ ] **Step 3: Run all tests**

```bash
./gradlew test
```

Expected: all tests PASS (VideoIdParserTest + TestYoutubeVideoDownloaderScript)

If `TestYoutubeVideoDownloaderScript` fails due to field injection issues, adjust the injection approach: make `extractor` and `downloader` internal properties with `internal` visibility and inject via constructor instead of reflection, then update the script class accordingly.

- [ ] **Step 4: Commit**

```bash
git rm src/test/java/TestSampleScript.kt
git add src/test/java/TestYoutubeVideoDownloaderScript.kt
git commit -m "test: replace sample script test with YouTube downloader test"
```

---

## Task 12: Final Build Verification

- [ ] **Step 1: Run the full build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Verify the shadow JAR is produced**

```bash
./gradlew shadowJar
ls build/libs/
```

Expected: `release.jar` present

- [ ] **Step 3: Commit if any fixes were needed, otherwise tag the plan as done**

```bash
git log --oneline -15
```

Review the commit history to make sure every task produced a meaningful commit.

---

## Known Limitations / Notes for Implementer

- **NewPipeExtractor and ProGuard:** NewPipeExtractor uses reflection internally. If `./gradlew scriptJar` (the ProGuard step) strips needed classes, add keep rules to `proguard-rules/script.pro`. A starting rule: `-keep class org.schabi.newpipe.** { *; }`
- **`getTitle` caching:** `YouTubeExtractor.getTitle()` makes a separate network call. If performance matters, cache the `StreamExtractor` between `getStreams()` and `getTitle()` calls within a single execution.
- **NewPipeExtractor thread-safety:** `NewPipe.init()` should only be called once. If the Cereal runtime creates multiple script instances, guard the `init` call with a companion object flag.
- **Test injection pattern:** The reflection-based field injection in `TestYoutubeVideoDownloaderScript` is a pragmatic workaround for Cereal's script lifecycle. If it proves brittle, refactor `YoutubeVideoDownloaderScript` to accept `extractor` and `downloader` via a secondary constructor with defaults.
