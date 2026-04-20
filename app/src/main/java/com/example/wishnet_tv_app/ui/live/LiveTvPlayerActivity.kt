package com.example.wishnet_tv_app.ui.live

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.data.AppContainer
import com.example.wishnet_tv_app.data.model.LiveChannelItem
import com.example.wishnet_tv_app.utils.LiveTvPrefs
import kotlinx.coroutines.launch

class LiveTvPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var imgChannelLogo: ImageView
    private lateinit var txtChannelNumber: TextView
    private lateinit var txtChannelName: TextView

    private var player: ExoPlayer? = null

    private val repository by lazy { AppContainer.liveTvRepository(this) }
    private val liveTvPrefs by lazy { LiveTvPrefs(this) }

    private var grid: List<LiveChannelItem> = emptyList()
    private var currentChannelIndex: Int = -1
    private var currentStreamUrl: String? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private var pendingShowLoading = false
    private var pendingRecovery = false

    private val delayedShowLoading = Runnable {
        if (pendingShowLoading) {
            loadingOverlay.visibility = View.VISIBLE
        }
    }

    private val delayedRecovery = Runnable {
        if (!pendingRecovery) return@Runnable

        Log.w("LiveTvPlayer", "Recovery automático del canal actual")
        reloadCurrentChannelStream()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv_player)

        playerView = findViewById(R.id.playerView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        imgChannelLogo = findViewById(R.id.imgChannelLogo)
        txtChannelNumber = findViewById(R.id.txtChannelNumber)
        txtChannelName = findViewById(R.id.txtChannelName)

        initializePlayer()
        loadLiveGrid()
    }

    private fun initializePlayer() {
        if (player != null) return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                4000,
                15000,
                1800,
                3000
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            .also { exoPlayer ->
                playerView.player = exoPlayer
                playerView.useController = false
                exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                Log.d("LiveTvPlayer", "STATE_BUFFERING")
                                showLoadingOverlayDelayed()
                                scheduleRecovery()
                            }

                            Player.STATE_READY -> {
                                Log.d("LiveTvPlayer", "STATE_READY")
                                cancelRecovery()
                                hideLoadingOverlay()
                            }

                            Player.STATE_ENDED -> {
                                Log.d("LiveTvPlayer", "STATE_ENDED")
                                showLoadingOverlayDelayed()
                                scheduleRecovery()
                            }

                            Player.STATE_IDLE -> {
                                Log.d("LiveTvPlayer", "STATE_IDLE")
                            }
                        }
                    }

                    override fun onIsLoadingChanged(isLoading: Boolean) {
                        Log.d("LiveTvPlayer", "isLoading = $isLoading")
                    }

                    override fun onRenderedFirstFrame() {
                        Log.d("LiveTvPlayer", "onRenderedFirstFrame")
                        cancelRecovery()
                        hideLoadingOverlay()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(
                            "LiveTvPlayer",
                            "Player error: ${error.errorCodeName} - ${error.message}",
                            error
                        )
                        showLoadingOverlayDelayed()
                        scheduleRecovery()
                    }
                })
            }
    }

    private fun loadLiveGrid() {
        lifecycleScope.launch {
            val result = repository.getLiveGrid()

            result.onSuccess { response ->
                if (!response.ok) {
                    Log.e("LiveTvPlayer", "Respuesta no OK en /live")
                    return@onSuccess
                }

                grid = response.grid.filter { it.enabled }

                if (grid.isEmpty()) {
                    Log.e("LiveTvPlayer", "La grilla vino vacía")
                    return@onSuccess
                }

                val lastChannelId = liveTvPrefs.getLastChannelId()
                val indexFromPrefs =
                    if (!lastChannelId.isNullOrBlank()) {
                        repository.findChannelIndex(grid, lastChannelId)
                    } else {
                        -1
                    }

                currentChannelIndex = if (indexFromPrefs >= 0) {
                    indexFromPrefs
                } else {
                    0
                }

                playCurrentChannel()
            }.onFailure { error ->
                Log.e("LiveTvPlayer", "Error cargando /live: ${error.message}", error)
            }
        }
    }

    private fun playCurrentChannel() {
        val channel = grid.getOrNull(currentChannelIndex) ?: return

        currentStreamUrl = null
        showChannelLoading(channel)

        lifecycleScope.launch {
            val result = repository.getPlayInfo(channel.id)

            result.onSuccess { response ->
                val streamUrl = response.playback?.streamUrl

                Log.d("LiveTvPlayer", "response.ok = ${response.ok}")
                Log.d("LiveTvPlayer", "strategy = ${response.strategy}")
                Log.d("LiveTvPlayer", "streamUrl = $streamUrl")
                Log.d("LiveTvPlayer", "directSourceUrl = ${response.playback?.directSourceUrl}")

                if (!response.ok || streamUrl.isNullOrBlank()) {
                    Log.e("LiveTvPlayer", "No se pudo resolver el canal ${channel.id}")
                    return@onSuccess
                }

                currentStreamUrl = streamUrl
                prepareAndPlay(streamUrl)
                liveTvPrefs.saveLastChannelId(channel.id)
            }.onFailure { error ->
                Log.e("LiveTvPlayer", "Error en /play: ${error.message}", error)
            }
        }
    }

    private fun reloadCurrentChannelStream() {
        val streamUrl = currentStreamUrl

        if (streamUrl.isNullOrBlank()) {
            Log.w("LiveTvPlayer", "No hay streamUrl actual para recovery, resolviendo canal otra vez")
            playCurrentChannel()
            return
        }

        Log.w("LiveTvPlayer", "Reintentando streamUrl actual")
        prepareAndPlay(streamUrl)
    }

    private fun prepareAndPlay(streamUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(5000)
                    .setMinPlaybackSpeed(0.97f)
                    .setMaxPlaybackSpeed(1.03f)
                    .build()
            )
            .build()

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("WishNetTV/1.0")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(false)
            .createMediaSource(mediaItem)

        player?.apply {
            playWhenReady = false
            stop()
            clearMediaItems()
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    private fun showChannelLoading(channel: LiveChannelItem) {
        txtChannelNumber.text = "Canal ${channel.numero}"
        txtChannelName.text = channel.name

        if (!channel.logo.isNullOrBlank()) {
            Glide.with(this)
                .load(channel.logo)
                .into(imgChannelLogo)
        } else {
            imgChannelLogo.setImageDrawable(null)
        }

        showLoadingOverlayImmediate()
    }

    private fun showLoadingOverlayImmediate() {
        pendingShowLoading = false
        uiHandler.removeCallbacks(delayedShowLoading)
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun showLoadingOverlayDelayed() {
        pendingShowLoading = true
        uiHandler.removeCallbacks(delayedShowLoading)
        uiHandler.postDelayed(delayedShowLoading, 1200)
    }

    private fun hideLoadingOverlay() {
        pendingShowLoading = false
        uiHandler.removeCallbacks(delayedShowLoading)
        loadingOverlay.visibility = View.GONE
    }

    private fun scheduleRecovery() {
        pendingRecovery = true
        uiHandler.removeCallbacks(delayedRecovery)
        uiHandler.postDelayed(delayedRecovery, 8000)
    }

    private fun cancelRecovery() {
        pendingRecovery = false
        uiHandler.removeCallbacks(delayedRecovery)
    }

    private fun zapNext() {
        if (grid.isEmpty()) return
        cancelRecovery()
        currentChannelIndex =
            if (currentChannelIndex + 1 > grid.lastIndex) 0 else currentChannelIndex + 1
        playCurrentChannel()
    }

    private fun zapPrevious() {
        if (grid.isEmpty()) return
        cancelRecovery()
        currentChannelIndex =
            if (currentChannelIndex - 1 < 0) grid.lastIndex else currentChannelIndex - 1
        playCurrentChannel()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                zapNext()
                true
            }

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                zapPrevious()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()
        cancelRecovery()
        hideLoadingOverlay()
        playerView.player = null
        player?.release()
        player = null
    }
}