package com.shortsfactory.domain.export

/** Nome de arquivo estável e único por export: `<shortId>_<platform>_<quality>_<res>_<fps>_<fingerprint>.mp4`. */
object ExportFileNaming {
    const val PART_SUFFIX = ".part"

    fun directory(projectId: Long): String = "exports/$projectId"

    fun fingerprint(
        startMs: Long,
        endMs: Long,
        intervalVersion: Int,
        platform: String,
        quality: String,
        resolution: String,
        fps: Int,
        subtitleStyle: String = ""
    ): String {
        val input = listOf(startMs, endMs, intervalVersion, platform, quality, resolution, fps, subtitleStyle).joinToString("|")
        var hash = -0x340d631b7bdddcdbL
        for (b in input.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (b.toLong() and 0xff)) * 0x100000001b3L
        }
        return java.lang.Long.toHexString(hash).padStart(16, '0').take(FINGERPRINT_LENGTH)
    }

    fun fileName(
        shortId: Long,
        platform: String,
        quality: String,
        resolution: String,
        fps: Int,
        fingerprint: String
    ): String = listOf(shortId.toString(), part(platform), part(quality), part(resolution), fps.toString(), part(fingerprint))
        .joinToString("_") + ".mp4"

    fun partName(finalName: String): String = finalName + PART_SUFFIX

    private fun part(value: String): String =
        value.lowercase().replace('×', 'x').replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "x" }

    private const val FINGERPRINT_LENGTH = 10
}
