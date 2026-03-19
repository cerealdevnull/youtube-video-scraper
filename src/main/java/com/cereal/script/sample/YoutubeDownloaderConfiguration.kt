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
