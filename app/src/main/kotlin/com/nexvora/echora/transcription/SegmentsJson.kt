package com.nexvora.echora.transcription

import org.json.JSONArray
import org.json.JSONObject

object SegmentsJson {
    fun encode(segments: List<Segment>): String = JSONArray().also { a ->
        segments.forEach { a.put(JSONObject().put("s", it.startMs).put("e", it.endMs).put("t", it.text)) }
    }.toString()

    fun decode(json: String): List<Segment> = runCatching {
        val a = JSONArray(json)
        List(a.length()) { i -> a.getJSONObject(i).let { Segment(it.getLong("s"), it.getLong("e"), it.getString("t")) } }
    }.getOrDefault(emptyList())
}
