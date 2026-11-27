package com.nexvora.echora.transcription

import java.util.Locale

/** Plain-text / SRT / VTT writers. SRT and VTT require real timestamps; they refuse rather than invent them. */
object TranscriptExporter {
    fun toTxt(text: String): String = text.trim() + "\n"

    fun hasTimestamps(segments: List<Segment>) = segments.isNotEmpty()

    fun toSrt(segments: List<Segment>): String {
        require(hasTimestamps(segments)) { NO_TIMESTAMPS }
        return buildString {
            segments.forEachIndexed { i, s ->
                append(i + 1).append('\n')
                append(stamp(s.startMs, ',')).append(" --> ").append(stamp(s.endMs, ',')).append('\n')
                append(s.text.trim()).append("\n\n")
            }
        }
    }

    fun toVtt(segments: List<Segment>): String {
        require(hasTimestamps(segments)) { NO_TIMESTAMPS }
        return buildString {
            append("WEBVTT\n\n")
            segments.forEach { s ->
                append(stamp(s.startMs, '.')).append(" --> ").append(stamp(s.endMs, '.')).append('\n')
                append(s.text.trim()).append("\n\n")
            }
        }
    }

    internal fun stamp(ms: Long, sep: Char): String {
        val t = ms.coerceAtLeast(0)
        return String.format(Locale.ROOT, "%02d:%02d:%02d%c%03d", t / 3_600_000, t / 60_000 % 60, t / 1000 % 60, sep, t % 1000)
    }

    const val NO_TIMESTAMPS = "This transcript has no timestamps (the engine did not provide any), so SRT/VTT cannot be created."
}
