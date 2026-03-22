The YouTube Video Downloader is a robust automation script that allows you to easily download high-quality videos and audio directly from YouTube. Simply provide a YouTube URL or Video ID, select your desired quality, and the script handles the rest, automatically saving the media to your Downloads folder.

## Features

- **Quality Selection**: Choose your preferred download quality (from 360P up to 1080P and BEST) or opt for AUDIO_ONLY.
- **Smart Fallback**: Automatically falls back to the next highest available quality if the requested resolution isn't supported for a particular video.
- **Cross-Platform Support**: Seamlessly runs on Windows, macOS, and Linux out of the box.
- **Automatic Muxing**: Downloads separate high-quality video and audio streams and seamlessly merges them together for resolutions like 1080P.

## Use Cases

- **Offline Viewing**: Save videos locally to watch during flights, commutes, or in areas with poor internet connectivity.
- **Content Archival**: Keep personal backups of favorite tutorials, lectures, or music videos.
- **Audio Extraction**: Easily extract just the audio track from videos for podcasts or music listening.

## Requirements

- **FFmpeg**: Required for downloading 1080P or BEST quality videos. The script automatically merges high-definition video and audio streams using `ffmpeg`. Please ensure `ffmpeg` is installed and accessible on your system's PATH. (Not required for standard 720P, 480P, 360P, or AUDIO_ONLY downloads).
