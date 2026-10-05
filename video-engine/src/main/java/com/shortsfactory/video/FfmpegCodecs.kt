package com.shortsfactory.video

/** Conjunto fechado de codecs usados com o binário embutido (build `--disable-gpl`, sem libx264/libmp3lame). */
internal object FfmpegCodecs {
    const val VIDEO_ENCODER = "h264_mediacodec"
    const val AUDIO_ENCODER = "aac"
    const val AUDIO_EXTENSION = "m4a"
    const val AUDIO_BITRATE = "128k"
    const val ANALYSIS_AUDIO_BITRATE = "64k"
}
