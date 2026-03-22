package com.cerealautomation.youtubedownloader

enum class DownloadQuality(val displayName: String) {
    BEST("Best Available"),
    Q1080P("1080p (Full HD)"),
    Q720P("720p (HD)"),
    Q480P("480p (SD)"),
    Q360P("360p (Low)"),
    AUDIO_ONLY("Audio Only");

    override fun toString(): String = displayName
}
