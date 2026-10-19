package com.alveo.tube.core.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alveo.tube.core.model.StreamResult
import java.util.concurrent.atomic.AtomicBoolean

class StreamExtractor(private val context: Context) {

    private var backgroundWebView: WebView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isCompleted = AtomicBoolean(false)
    private var timeoutRunnable: Runnable? = null

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

            onStatusUpdate("Menginisialisasi engine WebView headless...")

            try {
                val webView = WebView(context.applicationContext)
                backgroundWebView = webView

                val settings = webView.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null

                        if (url.contains("googlevideo.com/videoplayback") && !isCompleted.getAndSet(true)) {
                            val elapsed = System.currentTimeMillis() - startTime
                            val uri = Uri.parse(url)
                            val mimeType = uri.getQueryParameter("mime") ?: "video/mp4"
                            val itag = uri.getQueryParameter("itag")?.toIntOrNull() ?: 0
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
                            onStatusUpdate("Halaman termuat, memicu playback & sniffing...")
                            // Trigger play via JS
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    var v = document.querySelector('video');
                                    if (v) { v.muted = true; v.play(); }
                                    var btn = document.querySelector('button.ytp-large-play-button');
                                    if (btn) { btn.click(); }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }
                }

                // Timeout after 20 seconds
                timeoutRunnable = Runnable {
                    if (!isCompleted.getAndSet(true)) {
                        onError("Batas waktu ekstraksi habis (timeout)")
                        cleanup()
                    }
                }
                mainHandler.postDelayed(timeoutRunnable!!, 20000)

                val targetUrl = "https://m.youtube.com/watch?v=$videoId"
                onStatusUpdate("Memuat link target ke engine...")
                webView.loadUrl(targetUrl)

            } catch (e: Exception) {
                if (!isCompleted.getAndSet(true)) {
                    onError("Gagal menginisialisasi WebView: ${e.message}")
                    cleanup()
                }
            }
        }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun cleanup() {
        cancelTimeout()
        mainHandler.post {
            backgroundWebView?.let { wv ->
                wv.stopLoading()
                wv.webViewClient = WebViewClient()
                wv.destroy()
            }
            backgroundWebView = null
        }
    }
}
