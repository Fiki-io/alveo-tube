package com.alveo.tube.core.extractor

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alveo.tube.core.model.StreamResult
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class StreamExtractor(private val webView: WebView) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val isCompleted = AtomicBoolean(false)
    private var timeoutRunnable: Runnable? = null
    private var domCheckRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun extractStream(
        videoId: String,
        onStatusUpdate: (String) -> Unit,
        onSuccess: (StreamResult) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            isCompleted.set(false)
            val startTime = System.currentTimeMillis()

            cleanup()

            onStatusUpdate("Menginisialisasi engine WebView aktif...")

            try {
                val settings = webView.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                webView.webChromeClient = WebChromeClient()

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null

                        // Engine 1: Sniff direct videoplayback request
                        if (url.contains("googlevideo.com/videoplayback") && !isCompleted.getAndSet(true)) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val uri = Uri.parse(url)
                            val mimeType = uri.getQueryParameter("mime") ?: "video/mp4"
                            val itag = uri.getQueryParameter("itag")?.toIntOrNull() ?: 18
                            val headers = request.requestHeaders ?: emptyMap()

                            mainHandler.post {
                                onStatusUpdate("Stream berhasil disadap (${elapsed}ms)")
                                cancelTimeout()
                                onSuccess(
                                    StreamResult(
                                        streamUrl = url,
                                        mimeType = mimeType,
                                        itag = itag,
                                        headers = headers,
                                        extractionLatencyMs = elapsed
                                    )
                                )
                                cleanup()
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!isCompleted.get()) {
                            onStatusUpdate("Halaman siap, memicu pemutaran & ekstraksi...")

                            // Trigger playback
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    var v = document.querySelector('video');
                                    if (v) { v.muted = true; v.play().catch(function(e){}); }
                                    var b = document.querySelector('.ytp-large-play-button') || document.querySelector('button');
                                    if (b) { b.click(); }
                                })();
                                """.trimIndent(),
                                null
                            )

                            // Engine 2: Extract from ytInitialPlayerResponse directly from DOM
                            pollPlayerResponse(startTime, onStatusUpdate, onSuccess)
                        }
                    }
                }

                // Safety timeout 15 seconds
                timeoutRunnable = Runnable {
                    if (!isCompleted.getAndSet(true)) {
                        onError("Batas waktu ekstraksi habis (timeout)")
                        cleanup()
                    }
                }
                mainHandler.postDelayed(timeoutRunnable!!, 15000)

                // Load Embed player with proper Referer (10x faster and lighter than full mobile watch page)
                val targetUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&enablejsapi=1"
                val extraHeaders = mapOf("Referer" to "https://www.youtube.com/")
                onStatusUpdate("Memuat player embed ke engine...")
                webView.loadUrl(targetUrl, extraHeaders)

            } catch (e: Exception) {
                if (!isCompleted.getAndSet(true)) {
                    onError("Gagal menginisialisasi engine: ${e.message}")
                    cleanup()
                }
            }
        }
    }

    private fun pollPlayerResponse(
        startTime: Long,
        onStatusUpdate: (String) -> Unit,
        onSuccess: (StreamResult) -> Unit
    ) {
        var attempts = 0
        domCheckRunnable = object : Runnable {
            override fun run() {
                if (isCompleted.get()) return

                attempts++
                webView.evaluateJavascript(
                    """
                    (function() {
                        try {
                            var pr = window.ytInitialPlayerResponse;
                            if (pr && pr.streamingData) {
                                var formats = (pr.streamingData.formats || []).concat(pr.streamingData.adaptiveFormats || []);
                                for (var i = 0; i < formats.length; i++) {
                                    if (formats[i].url) {
                                        return JSON.stringify({
                                            url: formats[i].url,
                                            mimeType: formats[i].mimeType || 'video/mp4',
                                            itag: formats[i].itag || 18
                                        });
                                    }
                                }
                            }
                        } catch(e) {}
                        return null;
                    })();
                    """.trimIndent()
                ) { resultJson ->
                    if (isCompleted.get()) return@evaluateJavascript

                    if (!resultJson.isNullOrEmpty() && resultJson != "null") {
                        try {
                            val cleanJson = if (resultJson.startsWith("\"") && resultJson.endsWith("\"")) {
                                // Unescape JSON string returned by evaluateJavascript
                                JSONObject("{\"d\":$resultJson}").getString("d")
                            } else {
                                resultJson
                            }

                            val obj = JSONObject(cleanJson)
                            val streamUrl = obj.optString("url")
                            if (streamUrl.isNotEmpty() && !isCompleted.getAndSet(true)) {
                                val elapsed = System.currentTimeMillis() - startTime
                                val mimeType = obj.optString("mimeType", "video/mp4")
                                val itag = obj.optInt("itag", 18)

                                onStatusUpdate("Stream diekstrak via DOM (${elapsed}ms)")
                                cancelTimeout()
                                onSuccess(
                                    StreamResult(
                                        streamUrl = streamUrl,
                                        mimeType = mimeType,
                                        itag = itag,
                                        headers = mapOf("Referer" to "https://www.youtube.com/"),
                                        extractionLatencyMs = elapsed
                                    )
                                )
                                cleanup()
                                return@evaluateJavascript
                            }
                        } catch (_: Exception) {
                            // Retry
                        }
                    }

                    if (attempts < 10 && !isCompleted.get()) {
                        mainHandler.postDelayed(this, 800)
                    }
                }
            }
        }
        mainHandler.postDelayed(domCheckRunnable!!, 500)
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
        domCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        domCheckRunnable = null
    }

    fun cleanup() {
        cancelTimeout()
        mainHandler.post {
            webView.stopLoading()
            webView.loadUrl("about:blank")
        }
    }
}
