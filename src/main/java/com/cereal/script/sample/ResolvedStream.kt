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
