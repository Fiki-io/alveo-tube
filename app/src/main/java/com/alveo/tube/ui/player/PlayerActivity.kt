package com.alveo.tube.ui.player

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.alveo.tube.R
import com.alveo.tube.core.extractor.StreamExtractor
import com.alveo.tube.core.model.MediaItem
import com.alveo.tube.core.model.StreamResult
import com.alveo.tube.core.player.PlaybackManager
import com.alveo.tube.databinding.ActivityPlayerBinding

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var streamExtractor: StreamExtractor
    private lateinit var playbackManager: PlaybackManager
    private var currentMediaItem: MediaItem? = null
    private var currentStreamResult: StreamResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentMediaItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_MEDIA_ITEM, MediaItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_MEDIA_ITEM) as? MediaItem
        }

        if (currentMediaItem == null) {
            finish()
            return
        }

        setupViews()
        setupEngines()
        startExtraction()
    }

    private fun setupViews() {
        val item = currentMediaItem ?: return
        binding.tvPlayerTopTitle.text = item.title
        binding.tvVideoTitleDetail.text = item.title
        binding.tvChannelTitleDetail.text = item.channelTitle

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.toggleModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnModeVideo -> {
                    binding.playerView.visibility = View.VISIBLE
                    binding.layoutAudioOnly.visibility = View.GONE
                }
                R.id.btnModeAudio -> {
                    binding.playerView.visibility = View.GONE
                    binding.layoutAudioOnly.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupEngines() {
        playbackManager = PlaybackManager(this)
        playbackManager.attachPlayerView(binding.playerView)
        streamExtractor = StreamExtractor(this)
    }

    private fun startExtraction() {
        val item = currentMediaItem ?: return
        binding.layoutPlayerLoading.visibility = View.VISIBLE

        streamExtractor.extractStream(
            videoId = item.id,
            onStatusUpdate = { status ->
                binding.tvLoadingStatus.text = status
                binding.tvDebugStatus.text = status
            },
            onSuccess = { result ->
                currentStreamResult = result
                binding.layoutPlayerLoading.visibility = View.GONE
                binding.tvDebugStatus.text = getString(R.string.debug_status_playing) + " (${result.extractionLatencyMs}ms)"
                binding.tvDebugDetails.text = buildString {
                    append("MIME: ").append(result.mimeType).append("\n")
                    append("ITAG: ").append(result.itag).append("\n")
                    append("Headers Disadap: ").append(result.headers.size).append(" entri\n")
                    append("URL Snippet: ").append(result.streamUrl.take(70)).append("...")
                }

                playbackManager.playStream(result) { isPlaying, isBuffering, errorMsg ->
                    when {
                        errorMsg != null -> {
                            binding.tvDebugStatus.text = getString(R.string.debug_status_error) + ": $errorMsg"
                        }
                        isBuffering -> {
                            binding.tvDebugStatus.text = getString(R.string.debug_status_buffering)
                        }
                        isPlaying -> {
                            binding.tvDebugStatus.text = getString(R.string.debug_status_playing)
                        }
                    }
                }
            },
            onError = { error ->
                binding.layoutPlayerLoading.visibility = View.GONE
                binding.tvDebugStatus.text = getString(R.string.debug_status_error)
                binding.tvDebugDetails.text = error
            }
        )
    }

    override fun onPause() {
        super.onPause()
        playbackManager.pause()
    }

    override fun onResume() {
        super.onResume()
        playbackManager.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        streamExtractor.cleanup()
        playbackManager.release()
    }

    companion object {
        const val EXTRA_MEDIA_ITEM = "extra_media_item"
    }
}
