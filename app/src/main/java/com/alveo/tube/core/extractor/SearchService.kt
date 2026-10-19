package com.alveo.tube.core.extractor

import com.alveo.tube.core.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class SearchService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // Check if query is a direct YouTube link or ID
        val extractedId = extractVideoId(trimmed)
        if (extractedId != null) {
            return@withContext listOf(
                MediaItem(
                    id = extractedId,
                    title = "Video: $extractedId",
                    channelTitle = "YouTube",
                    durationText = "--:--",
                    thumbnailUrl = "https://i.ytimg.com/vi/$extractedId/hqdefault.jpg"
                )
            )
        }

        val requestPayload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240410.01.00")
                    put("hl", "id")
                    put("gl", "ID")
                })
            })
            put("query", trimmed)
        }

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8")
            .post(requestPayload.toString().toRequestBody(jsonMediaType))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Content-Type", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val responseBody = response.body?.string() ?: return@withContext emptyList()
                return@withContext parseSearchResults(responseBody)
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
    }

    private fun parseSearchResults(jsonString: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        try {
            val root = JSONObject(jsonString)
            val contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return items

            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i) ?: continue
                val itemContents = section.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents") ?: continue

                for (j in 0 until itemContents.length()) {
                    val itemObj = itemContents.optJSONObject(j) ?: continue
                    val videoRenderer = itemObj.optJSONObject("videoRenderer") ?: continue

                    val videoId = videoRenderer.optString("videoId")
                    if (videoId.isEmpty()) continue

                    // Parse title
                    val titleRuns = videoRenderer.optJSONObject("title")?.optJSONArray("runs")
                    val titleBuilder = StringBuilder()
                    if (titleRuns != null) {
                        for (k in 0 until titleRuns.length()) {
                            titleBuilder.append(titleRuns.optJSONObject(k)?.optString("text", ""))
                        }
                    }
                    val title = titleBuilder.toString().ifEmpty { "Video" }

                    // Parse channel
                    val channelRuns = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")
                        ?: videoRenderer.optJSONObject("longBylineText")?.optJSONArray("runs")
                    val channel = channelRuns?.optJSONObject(0)?.optString("text", "Channel") ?: "Channel"

                    // Parse duration
                    val duration = videoRenderer.optJSONObject("lengthText")?.optString("simpleText", "--:--") ?: "--:--"

                    // Parse thumbnail
                    val thumbnails = videoRenderer.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    val lastThumb = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")
                    val thumbUrl = lastThumb ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    items.add(
                        MediaItem(
                            id = videoId,
                            title = title,
                            channelTitle = channel,
                            durationText = duration,
                            thumbnailUrl = thumbUrl
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore parse errors, return accumulated items
        }
        return items
    }

    private fun extractVideoId(input: String): String? {
        val patterns = listOf(
            Pattern.compile("(?:youtu\\.be/|youtube\\.com/(?:embed/|v/|watch\\?v=|watch\\?.+&v=))([\\w-]{11})"),
            Pattern.compile("^[\\w-]{11}$")
        )
        for (p in patterns) {
            val matcher = p.matcher(input)
            if (matcher.find()) {
                return matcher.group(1) ?: matcher.group(0)
            }
        }
        return null
    }
}
