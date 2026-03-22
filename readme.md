# YouTube Video Downloader

The YouTube Video Downloader is a script for [Cereal Automation](https://cereal-automation.com/) that allows users to easily download high-quality videos and audio directly from YouTube. 

## Features

- **Quality Selection**: Supports downloading at `BEST`, `1080P`, `720P`, `480P`, `360P`, and `AUDIO_ONLY`.
- **Smart Fallback**: Automatically falls back to the next highest available quality if the requested resolution isn't supported for a particular video.
- **Cross-Platform**: Seamlessly runs on Windows, macOS, and Linux out of the box.
- **Automatic Muxing**: Downloads separate high-quality video and audio streams and seamlessly merges them together for HD resolutions like 1080P.

## Prerequisites

* **Java 11 or higher**: Required to build and run the script.
* **FFmpeg**: Required on your system's PATH for downloading `1080P` or `BEST` quality videos. The script uses `ffmpeg` to automatically merge the high-definition video and audio streams.

## Development

1. Clone the repository:
```sh
git clone https://github.com/cerealdevnull/youtube-video-scraper.git
```
2. Open the project in your preferred IDE (IntelliJ IDEA is recommended).

### Running Tests

To verify the script is working properly, run the included test suite:
```sh
./gradlew test
```

## Creating a Release

The easiest way to build the script binary is by creating a git tag. This will trigger GitHub Actions to build a jar, update the `version_code`, obfuscate it, and automatically create a GitHub Release with the jar attached as an artifact.

To create a release:
1. Commit and push your changes.
2. Create and push a tag: `git tag v1.0.0 && git push origin v1.0.0`
3. The workflow will automatically build and create a GitHub Release using the tag as the version, with `release-<version>.jar` attached.

## CI/CD

A GitHub Actions configuration is included in this repository. It contains the following workflows:
* On each push to `master`, the test suite will run.
* When a tag is pushed, a script release JAR is generated and a GitHub Release is automatically created with the JAR attached.

## Additional Resources

For more detailed information on script development and publishing to the Cereal Marketplace:
* [Cereal Developer Documentation](https://docs.cereal-automation.com/)
* [Publishing Scripts Guide](https://docs.cereal-automation.com/developers/publishing/) - Learn how to publish your script to the Cereal Marketplace
