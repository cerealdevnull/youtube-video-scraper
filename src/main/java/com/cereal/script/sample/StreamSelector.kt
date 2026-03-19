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
                    // Then next-higher (closest above target)
                    ?: videoStreams.filter { it.heightPx > targetHeight }.minByOrNull { it.heightPx }
                    // Then absolute minimum if nothing above target either
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
