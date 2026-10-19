package com.alveo.tube.core.model

data class StreamResult(
    val streamUrl: String,
    val mimeType: String,
    val itag: Int,
    val headers: Map<String, String>,
    val extractionLatencyMs: Long
)
