package com.alveo.tube.core.extractor

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alveo.tube.core.model.StreamResult
import java.util.concurrent.atomic.AtomicBoolean

class StreamExtractor(private val webView: WebView) {

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

            onStatusUpdate("Menginisialisasi engine browser aktif...")

            try {
                val settings = webView.settings
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                // Use desktop user-agent to trigger full HTML5 player auto-buffering
                settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

                webView.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        if (!isCompleted.get() && newProgress < 100) {
                            onStatusUpdate("Memuat engine YouTube ($newProgress%)...")
                        }
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        val msg = consoleMessage?.message() ?: ""
                        if (msg.contains("play") || msg.contains("player") || msg.contains("error")) {
                            mainHandler.post {
                                if (!isCompleted.get()) {
                                    onStatusUpdate("Console: ${msg.take(40)}")
                                }
                            }
                        }
                        return true
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url?.toString() ?: return null

                        // Check for googlevideo stream URL
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
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (!isCompleted.get()) {
                            onStatusUpdate("Halaman siap, mengeksekusi buffer player...")

                            // Auto-accept Google consent banner if present and trigger play
                            view?.evaluateJavascript(
                                """
                                (function() {
                                    // 1. Click accept consent button if present
                                    var buttons = Array.from(document.querySelectorAll('button'));
                                    var consentBtn = buttons.find(function(b) {
                                        var t = b.innerText || '';
                                        return t.includes('Accept') || t.includes('Agree') || t.includes('Setuju');
                                    });
                                    if (consentBtn) { consentBtn.click(); }

                                    // 2. Play video to trigger stream buffering
                                    var v = document.querySelector('video');
                                    if (v) {
                                        v.muted = true;
                                        v.play().catch(function(e) {});
                                    }
                                    var playBtn = document.querySelector('.ytp-large-play-button') || document.querySelector('.ytp-play-button');
                                    if (playBtn) { playBtn.click(); }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }
                }

                // 30 seconds timeout
                timeoutRunnable = Runnable {
                    if (!isCompleted.getAndSet(true)) {
                        onError("Batas waktu ekstraksi habis (30 detik). Cek koneksi atau coba video lain.")
                    }
                }
                mainHandler.postDelayed(timeoutRunnable!!, 30000)

                // Load desktop watch URL (which pre-buffers stream automatically)
                val targetUrl = "https://www.youtube.com/watch?v=$videoId"
                onStatusUpdate("Menghubungkan ke: youtube.com/watch?v=$videoId")
                webView.loadUrl(targetUrl)

            } catch (e: Exception) {
                if (!isCompleted.getAndSet(true)) {
                    onError("Gagal menginisialisasi engine: ${e.message}")
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
            webView.stopLoading()
        }
    }
}
