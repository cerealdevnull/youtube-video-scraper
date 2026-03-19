# YouTube Video Downloader — Design Spec

**Date:** 2026-03-19  
**Status:** Approved

---

## Overview

Implement a Cereal Automation script that downloads a YouTube video to the user's `~/Downloads` folder. The user provides a YouTube URL or video ID and selects a preferred quality level via the Cereal configuration UI. The script resolves the direct download URL using NewPipeExtractor, downloads the stream(s) with OkHttp, and muxes them with ffmpeg when necessary.

---

## Architecture

Three focused components with clear boundaries:

| Component | Responsibility |
|---|---|
| `YoutubeVideoDownloaderScript` | Cereal `Script<C>` entry point. Lifecycle (license check, execute, finish), reads config, orchestrates the flow. |
| `YouTubeExtractor` | Wraps NewPipeExtractor. Accepts a video ID, returns a list of `ResolvedStream` objects (URL, quality label, MIME type, whether audio is separate). |
| `VideoDownloader` | Accepts a `ResolvedStream` and a title. Downloads video (and optionally audio) via OkHttp to `~/Downloads`. Calls ffmpeg to mux if the stream has a separate audio track. |

Supporting types:
- `ResolvedStream` — data class: `videoUrl`, `audioUrl?`, `qualityLabel`, `extension`, `needsMux`
- `DownloadQuality` — sealed enum: `BEST`, `Q1080P`, `Q720P`, `Q480P`, `Q360P`, `AUDIO_ONLY`

---

## Configuration (`YoutubeDownloaderConfiguration`)

Implements Cereal `ScriptConfiguration`. Exposes two user-facing fields:

| Field | Annotation | Type | Notes |
|---|---|---|---|
| `videoUrl()` | `@ScriptConfigurationItem` | `String` | Full YouTube URL or raw video ID |
| `quality()` | `@ScriptConfigurationItem` | `String` (enum label) | `BEST`, `1080P`, `720P`, `480P`, `360P`, `AUDIO_ONLY` |

The `quality()` field will be rendered as a dropdown/select in the Cereal UI using a string matching one of the `DownloadQuality` enum values.

---

## Data Flow

```
Cereal runtime calls execute()
  │
  1. Parse videoUrl config value → extract video ID
  │   Supported URL formats:
  │     - https://www.youtube.com/watch?v=<id>
  │     - https://youtu.be/<id>
  │     - Raw 11-character video ID
  │
  2. YouTubeExtractor.getStreams(videoId)
  │   └─ NewPipeExtractor:
  │        - Fetch video metadata via InnerTube API
  │        - Resolve stream URLs (handles signature deciphering, throttle bypass)
  │        - Return List<ResolvedStream>
  │
  3. Select stream matching requested quality
  │   - BEST      → highest-resolution video stream regardless of whether it is
  │                 a combined (muxed) or adaptive (video-only + separate audio) stream
  │   - 1080P/720P/480P/360P → closest matching video stream; fall back to next-lower
  │                 quality if the requested resolution is unavailable
  │   - AUDIO_ONLY → best bitrate audio-only stream
  │   - Log chosen quality if it differs from requested
  │
  4. VideoDownloader.download(stream, videoTitle)
  │   a. Download video track via OkHttp GET
  │      → ~/Downloads/<sanitized-title>.<ext>  (or temp file if mux needed)
  │   b. If stream.audioUrl != null:
  │      → Download audio track to temp file
  │   c. If stream.needsMux:
  │      → ffmpeg -i video_tmp -i audio_tmp -c copy ~/Downloads/<title>.mp4
  │      → Delete temp files
  │
  5. Log "Downloaded: ~/Downloads/<filename>"
  6. Return ExecutionResult.Success
```

---

## Error Handling

| Failure point | Behavior |
|---|---|
| Invalid URL / unrecognised format | Log descriptive error, return `ExecutionResult.Error` |
| Video unavailable, private, or geo-blocked | Catch NewPipeExtractor exception, log reason, return `ExecutionResult.Error` |
| No stream matches requested quality | Fall back to next-best available quality, log a warning, continue |
| No streams returned at all | Return `ExecutionResult.Error("No downloadable streams found")` |
| OkHttp network failure | Return `ExecutionResult.Error` with HTTP status or exception message |
| ffmpeg not found on PATH | Return `ExecutionResult.Error("ffmpeg is required for HD downloads. Install from https://ffmpeg.org")` |
| ffmpeg exits non-zero | Return `ExecutionResult.Error` with ffmpeg stderr |
| License check error | Existing `LicenseChecker` pattern — return `false` from `onStart()` to defer |

---

## Dependencies

Add to `gradle/libs.versions.toml` and `build.gradle.kts`:

| Library | Purpose |
|---|---|
| `com.github.TeamNewPipe:NewPipeExtractor:<latest>` | YouTube metadata + stream URL resolution |
| `com.squareup.okhttp3:okhttp:<latest>` | HTTP download |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | Already present — used for async download if needed |

NewPipeExtractor is available via JitPack. OkHttp is a standard Maven Central library.

**System dependency:** `ffmpeg` must be installed and on `PATH` for any stream where `needsMux = true`. The `needsMux` flag is set by `YouTubeExtractor` when the selected stream consists of a separate video track and audio track. This is determined at runtime from the stream metadata — the downloader does not need to reason about quality levels to decide whether to mux.

---

## File Naming

Output file name: `<sanitized-video-title>.<extension>`

Sanitization rules:
- Replace characters not valid in file names (`/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|`) with `_`
- Trim leading/trailing whitespace
- Truncate to 200 characters max

If a file with that name already exists in `~/Downloads`, append ` (1)`, ` (2)`, etc.

---

## Testing

| Test | Type | Notes |
|---|---|---|
| `VideoIdParserTest` | Unit | URL parsing for all three input formats |
| `YouTubeExtractorTest` | Unit | Mock NewPipeExtractor; verify stream selection logic |
| `VideoDownloaderTest` | Unit | Mock OkHttp + ffmpeg; verify file naming, temp file cleanup |
| `YoutubeVideoDownloaderScriptTest` | Integration (unit) | Uses existing `TestSampleScript` harness with MockK |
| Manual integration test | Manual | Run against a real short public video, verify file in ~/Downloads |

---

## Out of Scope

- Playlist support
- Subtitle/caption downloading
- Progress reporting beyond log messages
- Resumable downloads (partial file recovery)
- Cookie/authentication support (private videos)
