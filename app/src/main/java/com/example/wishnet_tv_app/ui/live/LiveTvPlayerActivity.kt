package com.example.wishnet_tv_app.ui.live

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.example.wishnet_tv_app.R
import com.example.wishnet_tv_app.data.AppContainer
import com.example.wishnet_tv_app.data.model.LiveChannelItem
import com.example.wishnet_tv_app.data.model.PlayResponse
import com.example.wishnet_tv_app.ui.login.LoginEmailActivity
import com.example.wishnet_tv_app.utils.LiveTvPrefs
import com.example.wishnet_tv_app.utils.SessionExpiredException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LiveTvPlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var imgChannelLogo: ImageView
    private lateinit var txtChannelNumber: TextView
    private lateinit var txtChannelName: TextView

    private lateinit var infoOverlay: LinearLayout
    private lateinit var txtInfoNumber: TextView
    private lateinit var txtInfoName: TextView
    private lateinit var txtInfoDetails: TextView

    private lateinit var statusOverlay: LinearLayout
    private lateinit var txtStatusMessage: TextView

    private lateinit var channelGuidePanel: LinearLayout
    private lateinit var channelGuideScroll: ScrollView
    private lateinit var channelGuideList: LinearLayout

    private var player: ExoPlayer? = null

    private val repository by lazy { AppContainer.liveTvRepository(this) }
    private val sessionManager by lazy { AppContainer.sessionManager(this) }
    private val liveTvPrefs by lazy { LiveTvPrefs(this) }

    private var grid: List<LiveChannelItem> = emptyList()
    private var currentChannelIndex: Int = -1
    private var guideSelectedIndex: Int = -1

    private var currentStreamUrl: String? = null
    private var currentStrategy: String? = null
    private var currentNodeName: String? = null
    private var currentNodeCode: String? = null
    private var currentPlaybackMode: String? = null

    private var playJob: Job? = null
    private var presenceJob: Job? = null

    private val uiHandler = Handler(Looper.getMainLooper())

    private var pendingShowLoading = false
    private var pendingRecovery = false
    private var isChangingChannel = false
    private var recoveryAttempt = 0

    private val maxRecoveryAttempts = 3
    private val guideAutoCloseMs = 5000L

    private val delayedShowLoading = Runnable {
        if (pendingShowLoading) {
            loadingOverlay.visibility = View.VISIBLE
        }
    }

    private val hideInfoOverlayRunnable = Runnable {
        infoOverlay.visibility = View.GONE
    }

    private val hideStatusOverlayRunnable = Runnable {
        statusOverlay.visibility = View.GONE
    }

    private val guideAutoCloseRunnable = Runnable {
        if (isGuideOpen()) {
            closeGuide()
        }
    }

    private val delayedRecovery = Runnable {
        if (!pendingRecovery || isChangingChannel) return@Runnable
        runSmartRecovery()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (sessionManager.getToken().isNullOrBlank()) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_live_tv_player)

        bindViews()
        initializePlayer()
        startPresenceLoop()
        loadLiveGrid()
    }

    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        imgChannelLogo = findViewById(R.id.imgChannelLogo)
        txtChannelNumber = findViewById(R.id.txtChannelNumber)
        txtChannelName = findViewById(R.id.txtChannelName)

        infoOverlay = findViewById(R.id.infoOverlay)
        txtInfoNumber = findViewById(R.id.txtInfoNumber)
        txtInfoName = findViewById(R.id.txtInfoName)
        txtInfoDetails = findViewById(R.id.txtInfoDetails)

        statusOverlay = findViewById(R.id.statusOverlay)
        txtStatusMessage = findViewById(R.id.txtStatusMessage)

        channelGuidePanel = findViewById(R.id.channelGuidePanel)
        channelGuideScroll = findViewById(R.id.channelGuideScroll)
        channelGuideList = findViewById(R.id.channelGuideList)
    }

    private fun initializePlayer() {
        if (player != null) return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,
                2000,
                250,
                500
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
                                showLoadingOverlayDelayed()
                                scheduleRecovery()
                            }

                            Player.STATE_READY -> {
                                isChangingChannel = false
                                recoveryAttempt = 0
                                cancelRecovery()
                                hideLoadingOverlay()
                                showInfoOverlay(autoHide = true)
                            }

                            Player.STATE_ENDED -> {
                                showLoadingOverlayDelayed()
                                scheduleRecovery()
                            }

                            Player.STATE_IDLE -> Unit
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        isChangingChannel = false
                        recoveryAttempt = 0
                        cancelRecovery()
                        hideLoadingOverlay()
                        showInfoOverlay(autoHide = true)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(
                            "LiveTvPlayer",
                            "Player error: ${error.errorCodeName} - ${error.message}",
                            error
                        )

                        showStatus("Problema de señal. Reintentando...")
                        showLoadingOverlayDelayed()
                        scheduleRecovery(delayMs = 2500)
                    }
                })
            }
    }

    private fun loadLiveGrid() {
        lifecycleScope.launch {
            val result = repository.getLiveGrid()

            result.onSuccess { response ->
                if (!response.ok) {
                    showStatus(response.message ?: "No se pudo cargar la grilla")
                    return@onSuccess
                }

                grid = response.grid
                    .filter { it.enabled }
                    .sortedBy { it.orden }

                if (grid.isEmpty()) {
                    showStatus("No hay canales disponibles")
                    return@onSuccess
                }

                val lastChannelId = liveTvPrefs.getLastChannelId()
                val indexFromPrefs =
                    if (!lastChannelId.isNullOrBlank()) {
                        repository.findChannelIndex(grid, lastChannelId)
                    } else {
                        -1
                    }

                currentChannelIndex = if (indexFromPrefs >= 0) indexFromPrefs else 0
                guideSelectedIndex = currentChannelIndex

                playCurrentChannel(resetRecovery = true)
            }.onFailure { error ->
                handleError(error, "Error cargando grilla")
            }
        }
    }

    private fun playCurrentChannel(resetRecovery: Boolean = true) {
        val channel = grid.getOrNull(currentChannelIndex) ?: return

        playJob?.cancel()

        if (resetRecovery) {
            recoveryAttempt = 0
        }

        currentStreamUrl = null
        isChangingChannel = true

        closeGuide()
        showChannelLoading(channel)
        updateInfoOverlay(channel)

        playJob = lifecycleScope.launch {
            val result = repository.getPlayInfo(channel.id)

            result.onSuccess { response ->
                handlePlayResponse(channel, response)
            }.onFailure { error ->
                isChangingChannel = false
                handleError(error, "Error resolviendo canal")
            }
        }
    }

    private fun handlePlayResponse(channel: LiveChannelItem, response: PlayResponse) {
        val streamUrl = response.playback?.streamUrl

        if (!response.ok || streamUrl.isNullOrBlank()) {
            isChangingChannel = false
            showStatus(response.message ?: "No se pudo reproducir el canal")
            return
        }

        currentStreamUrl = streamUrl
        currentStrategy = response.strategy ?: "-"
        currentNodeName = response.node?.nombre
        currentNodeCode = response.node?.codigo
        currentPlaybackMode = response.playback.mode ?: "proxy-ts"

        updateInfoOverlay(channel)
        prepareAndPlay(streamUrl)
        liveTvPrefs.saveLastChannelId(channel.id)
    }

    private fun runSmartRecovery() {
        if (isChangingChannel) return

        recoveryAttempt += 1

        when (recoveryAttempt) {
            1 -> {
                showStatus("Reintentando señal...")
                reloadCurrentStreamUrl()
            }

            2 -> {
                showStatus("Resolviendo ruta nuevamente...")
                playCurrentChannel(resetRecovery = false)
            }

            3 -> {
                showStatus("Último reintento de señal...")
                playCurrentChannel(resetRecovery = false)
            }

            else -> {
                pendingRecovery = false
                hideLoadingOverlay()
                showStatus("No se pudo recuperar la señal")
            }
        }
    }

    private fun reloadCurrentStreamUrl() {
        val streamUrl = currentStreamUrl

        if (streamUrl.isNullOrBlank()) {
            playCurrentChannel(resetRecovery = false)
            return
        }

        prepareAndPlay(streamUrl)
    }

    private fun prepareAndPlay(streamUrl: String) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("WishNetTV/1.0")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val mediaItem = MediaItem.fromUri(streamUrl)

        val mediaSource =
            if (streamUrl.contains(".m3u8", ignoreCase = true)) {
                HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(false)
                    .createMediaSource(mediaItem)
            } else {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }

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

    private fun updateInfoOverlay(channel: LiveChannelItem) {
        txtInfoNumber.text = "Canal ${channel.numero}"
        txtInfoName.text = channel.name
        txtInfoDetails.text = ""
    }

    private fun showInfoOverlay(autoHide: Boolean) {
        val channel = grid.getOrNull(currentChannelIndex) ?: return

        updateInfoOverlay(channel)

        infoOverlay.visibility = View.VISIBLE
        uiHandler.removeCallbacks(hideInfoOverlayRunnable)

        if (autoHide) {
            uiHandler.postDelayed(hideInfoOverlayRunnable, 3000)
        }
    }

    private fun showStatus(message: String) {
        txtStatusMessage.text = message
        statusOverlay.visibility = View.VISIBLE

        uiHandler.removeCallbacks(hideStatusOverlayRunnable)
        uiHandler.postDelayed(hideStatusOverlayRunnable, 3000)
    }

    private fun showLoadingOverlayImmediate() {
        pendingShowLoading = false
        uiHandler.removeCallbacks(delayedShowLoading)
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun showLoadingOverlayDelayed() {
        pendingShowLoading = true
        uiHandler.removeCallbacks(delayedShowLoading)
        uiHandler.postDelayed(delayedShowLoading, 900)
    }

    private fun hideLoadingOverlay() {
        pendingShowLoading = false
        uiHandler.removeCallbacks(delayedShowLoading)
        loadingOverlay.visibility = View.GONE
    }

    private fun scheduleRecovery(delayMs: Long = 8500) {
        if (isChangingChannel) return
        if (recoveryAttempt >= maxRecoveryAttempts) return

        pendingRecovery = true
        uiHandler.removeCallbacks(delayedRecovery)
        uiHandler.postDelayed(delayedRecovery, delayMs)
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

        guideSelectedIndex = currentChannelIndex
        playCurrentChannel(resetRecovery = true)
    }

    private fun zapPrevious() {
        if (grid.isEmpty()) return

        cancelRecovery()

        currentChannelIndex =
            if (currentChannelIndex - 1 < 0) grid.lastIndex else currentChannelIndex - 1

        guideSelectedIndex = currentChannelIndex
        playCurrentChannel(resetRecovery = true)
    }

    private fun openGuide() {
        if (grid.isEmpty()) return

        guideSelectedIndex = if (currentChannelIndex >= 0) currentChannelIndex else 0
        renderGuide()
        channelGuidePanel.visibility = View.VISIBLE
        uiHandler.removeCallbacks(hideInfoOverlayRunnable)
        resetGuideAutoClose()
    }

    private fun closeGuide() {
        uiHandler.removeCallbacks(guideAutoCloseRunnable)
        channelGuidePanel.visibility = View.GONE
    }

    private fun isGuideOpen(): Boolean {
        return channelGuidePanel.visibility == View.VISIBLE
    }

    private fun resetGuideAutoClose() {
        uiHandler.removeCallbacks(guideAutoCloseRunnable)
        uiHandler.postDelayed(guideAutoCloseRunnable, guideAutoCloseMs)
    }

    private fun moveGuideSelection(delta: Int) {
        if (grid.isEmpty()) return

        guideSelectedIndex += delta

        if (guideSelectedIndex > grid.lastIndex) {
            guideSelectedIndex = 0
        }

        if (guideSelectedIndex < 0) {
            guideSelectedIndex = grid.lastIndex
        }

        renderGuide()
        scrollGuideToSelected()
        resetGuideAutoClose()
    }

    private fun selectGuideChannel() {
        if (guideSelectedIndex !in grid.indices) return

        currentChannelIndex = guideSelectedIndex
        closeGuide()
        cancelRecovery()
        playCurrentChannel(resetRecovery = true)
    }

    private fun renderGuide() {
        channelGuideList.removeAllViews()

        grid.forEachIndexed { index, channel ->
            val selected = index == guideSelectedIndex
            val current = index == currentChannelIndex

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = android.graphics.drawable.ColorDrawable(
                    when {
                        selected -> Color.parseColor("#4481A4C7")
                        current -> Color.parseColor("#3314B8A6")
                        else -> Color.TRANSPARENT
                    }
                )
            }

            val logo = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(34), dp(26)).apply {
                    marginEnd = dp(8)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }

            if (!channel.logo.isNullOrBlank()) {
                Glide.with(this)
                    .load(channel.logo)
                    .into(logo)
            } else {
                logo.setImageDrawable(null)
            }

            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val title = TextView(this).apply {
                text = "${channel.numero}. ${channel.name}"
                setTextColor(Color.WHITE)
                textSize = 11.5f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val subtitle = TextView(this).apply {
                text = channel.category ?: channel.sourceName ?: ""
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 9f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }

            val marker = TextView(this).apply {
                text = if (current) "●" else ""
                setTextColor(Color.parseColor("#22C55E"))
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dp(5)
                }
            }

            textContainer.addView(title)
            textContainer.addView(subtitle)

            row.addView(logo)
            row.addView(textContainer)
            row.addView(marker)

            row.setOnClickListener {
                guideSelectedIndex = index
                selectGuideChannel()
            }

            channelGuideList.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(3)
                }
            )
        }
    }

    private fun scrollGuideToSelected() {
        channelGuideScroll.post {
            val selectedView = channelGuideList.getChildAt(guideSelectedIndex)
            selectedView?.let {
                channelGuideScroll.smoothScrollTo(0, it.top - dp(16))
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isGuideOpen()) {
            resetGuideAutoClose()

            return when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    moveGuideSelection(-1)
                    true
                }

                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    moveGuideSelection(1)
                    true
                }

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    selectGuideChannel()
                    true
                }

                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_BACK -> {
                    closeGuide()
                    true
                }

                else -> true
            }
        }

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

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                openGuide()
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                showInfoOverlay(autoHide = true)
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun startPresenceLoop() {
        presenceJob?.cancel()

        presenceJob = lifecycleScope.launch {
            while (isActive) {
                repository.sendPresence()
                    .onFailure { error ->
                        if (error is SessionExpiredException) {
                            goToLogin()
                            return@launch
                        }

                        Log.w("LiveTvPlayer", "No se pudo enviar presencia: ${error.message}")
                    }

                delay(30_000)
            }
        }
    }

    private fun handleError(error: Throwable, prefix: String) {
        Log.e("LiveTvPlayer", "$prefix: ${error.message}", error)

        if (error is SessionExpiredException) {
            goToLogin()
            return
        }

        showStatus(error.message ?: prefix)
    }

    private fun goToLogin() {
        sessionManager.clearSession()

        val intent = Intent(this, LoginEmailActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)
        finish()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onStop() {
        super.onStop()

        playJob?.cancel()
        presenceJob?.cancel()

        cancelRecovery()
        hideLoadingOverlay()
        closeGuide()

        uiHandler.removeCallbacks(hideInfoOverlayRunnable)
        uiHandler.removeCallbacks(hideStatusOverlayRunnable)
        uiHandler.removeCallbacks(guideAutoCloseRunnable)

        playerView.player = null
        player?.release()
        player = null
    }
}