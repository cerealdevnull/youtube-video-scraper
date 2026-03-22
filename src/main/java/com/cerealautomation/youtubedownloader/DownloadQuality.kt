package com.cerealautomation.youtubedownloader

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
