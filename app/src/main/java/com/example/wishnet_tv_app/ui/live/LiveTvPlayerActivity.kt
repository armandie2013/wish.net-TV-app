package com.example.wishnet_tv_app.ui.live

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
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
import kotlinx.coroutines.CancellationException
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

    /*
        Controles táctiles para celulares.

        Nullable para que no rompa Android TV si algún layout alternativo
        no tiene estos IDs.
    */
    private var touchControlsOverlay: LinearLayout? = null
    private var btnTouchUp: TextView? = null
    private var btnTouchDown: TextView? = null
    private var btnTouchLeft: TextView? = null
    private var btnTouchRight: TextView? = null
    private var btnTouchOk: TextView? = null

    private var player: ExoPlayer? = null

    private val repository by lazy { AppContainer.liveTvRepository(this) }
    private val sessionManager by lazy { AppContainer.sessionManager(this) }
    private val liveTvPrefs by lazy { LiveTvPrefs(this) }

    private var grid: List<LiveChannelItem> = emptyList()
    private var currentChannelIndex: Int = -1
    private var guideSelectedIndex: Int = -1
    private var previousGuideSelectedIndex: Int = -1

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

    private var lastGuideMoveAt = 0L
    private val guideMoveThrottleMs = 55L

    private var guideScrollGeneration = 0L

    private val guideRows = mutableListOf<GuideRowView>()

    private data class GuideRowView(
        val row: LinearLayout,
        val selector: TextView,
        val title: TextView,
        val subtitle: TextView,
        val marker: TextView
    )

    private val delayedShowLoading = Runnable {
        if (pendingShowLoading) {
            loadingOverlay.visibility = View.VISIBLE
        }
    }

    private val hideInfoOverlayRunnable = Runnable {
        hideInfoOverlay()
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

        channelGuideScroll.isFocusable = false
        channelGuideList.isFocusable = false

        touchControlsOverlay = findViewByIdOrNull(R.id.touchControlsOverlay)
        btnTouchUp = findViewByIdOrNull(R.id.btnTouchUp)
        btnTouchDown = findViewByIdOrNull(R.id.btnTouchDown)
        btnTouchLeft = findViewByIdOrNull(R.id.btnTouchLeft)
        btnTouchRight = findViewByIdOrNull(R.id.btnTouchRight)
        btnTouchOk = findViewByIdOrNull(R.id.btnTouchOk)

        setupGuideTouchBehavior()
        setupTouchControls()
    }

    private fun <T : View> findViewByIdOrNull(id: Int): T? {
        return try {
            findViewById<T>(id)
        } catch (_: Exception) {
            null
        }
    }

    /*
        Corrige el comportamiento en celulares:
        mientras el usuario toca o desplaza la lista, la guía NO se cierra.
        Recién cuando levanta el dedo vuelve a empezar el contador de 5 segundos.
    */
    private fun setupGuideTouchBehavior() {
        channelGuidePanel.setOnTouchListener { _, event ->
            if (!isGuideOpen()) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    pauseGuideAutoClose()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    resetGuideAutoClose()
                }
            }

            false
        }

        channelGuideScroll.setOnTouchListener { _, event ->
            if (!isGuideOpen()) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    pauseGuideAutoClose()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    resetGuideAutoClose()
                }
            }

            false
        }

        channelGuideList.setOnTouchListener { _, event ->
            if (!isGuideOpen()) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {
                    pauseGuideAutoClose()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    resetGuideAutoClose()
                }
            }

            false
        }
    }

    private fun setupTouchControls() {
        val overlay = touchControlsOverlay ?: return

        val isTvDevice = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        /*
            En Android TV queda oculto.
            En celulares queda visible.
        */
        overlay.visibility =
            if (hasTouchscreen && !isTvDevice) View.VISIBLE else View.GONE

        overlay.setOnTouchListener { _, event ->
            if (!isGuideOpen()) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> pauseGuideAutoClose()
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> resetGuideAutoClose()
            }

            false
        }

        btnTouchUp?.setOnClickListener {
            if (isGuideOpen()) {
                pauseGuideAutoClose()
                moveGuideSelection(-1)
                resetGuideAutoClose()
            } else {
                zapNext()
            }
        }

        btnTouchDown?.setOnClickListener {
            if (isGuideOpen()) {
                pauseGuideAutoClose()
                moveGuideSelection(1)
                resetGuideAutoClose()
            } else {
                zapPrevious()
            }
        }

        btnTouchRight?.setOnClickListener {
            if (isGuideOpen()) {
                resetGuideAutoClose()
            } else {
                openGuide()
            }
        }

        btnTouchLeft?.setOnClickListener {
            if (isGuideOpen()) {
                closeGuide()
            } else {
                showInfoOverlay(autoHide = true)
            }
        }

        btnTouchOk?.setOnClickListener {
            if (isGuideOpen()) {
                pauseGuideAutoClose()
                selectGuideChannel()
            } else {
                showInfoOverlay(autoHide = true)
            }
        }
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
                previousGuideSelectedIndex = guideSelectedIndex

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
        hideInfoOverlay()
        showChannelLoading(channel)
        updateInfoOverlay(channel)

        playJob = lifecycleScope.launch {
            try {
                val result = repository.getPlayInfo(channel.id)

                result.onSuccess { response ->
                    handlePlayResponse(channel, response)
                }.onFailure { error ->
                    if (error is CancellationException) return@launch

                    isChangingChannel = false
                    handleError(error, "Error resolviendo canal")
                }
            } catch (error: CancellationException) {
                // Normal cuando el usuario cambia rápido de canal.
            } catch (error: Throwable) {
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
            .setUserAgent("SN-IPTV/1.0")
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

        closeGuide()
        updateInfoOverlay(channel)

        infoOverlay.visibility = View.VISIBLE
        uiHandler.removeCallbacks(hideInfoOverlayRunnable)

        if (autoHide) {
            uiHandler.postDelayed(hideInfoOverlayRunnable, 3000)
        }
    }

    private fun hideInfoOverlay() {
        uiHandler.removeCallbacks(hideInfoOverlayRunnable)
        infoOverlay.visibility = View.GONE
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
        hideInfoOverlay()
        closeGuide()

        currentChannelIndex =
            if (currentChannelIndex + 1 > grid.lastIndex) 0 else currentChannelIndex + 1

        guideSelectedIndex = currentChannelIndex
        previousGuideSelectedIndex = guideSelectedIndex

        playCurrentChannel(resetRecovery = true)
    }

    private fun zapPrevious() {
        if (grid.isEmpty()) return

        cancelRecovery()
        hideInfoOverlay()
        closeGuide()

        currentChannelIndex =
            if (currentChannelIndex - 1 < 0) grid.lastIndex else currentChannelIndex - 1

        guideSelectedIndex = currentChannelIndex
        previousGuideSelectedIndex = guideSelectedIndex

        playCurrentChannel(resetRecovery = true)
    }

    private fun openGuide() {
        if (grid.isEmpty()) return

        hideInfoOverlay()

        guideSelectedIndex = if (currentChannelIndex >= 0) currentChannelIndex else 0
        previousGuideSelectedIndex = guideSelectedIndex

        if (guideRows.size != grid.size) {
            renderGuide()
        } else {
            updateGuideSelectionViews()
        }

        channelGuidePanel.visibility = View.VISIBLE

        channelGuidePanel.post {
            forceGuideScrollToSelected(center = true)
        }

        resetGuideAutoClose()
    }

    private fun closeGuide() {
        uiHandler.removeCallbacks(guideAutoCloseRunnable)
        channelGuidePanel.visibility = View.GONE
    }

    private fun isGuideOpen(): Boolean {
        return channelGuidePanel.visibility == View.VISIBLE
    }

    private fun pauseGuideAutoClose() {
        uiHandler.removeCallbacks(guideAutoCloseRunnable)
    }

    private fun resetGuideAutoClose() {
        uiHandler.removeCallbacks(guideAutoCloseRunnable)

        if (isGuideOpen()) {
            uiHandler.postDelayed(guideAutoCloseRunnable, guideAutoCloseMs)
        }
    }

    private fun moveGuideSelection(delta: Int) {
        if (grid.isEmpty()) return

        val now = System.currentTimeMillis()
        if (now - lastGuideMoveAt < guideMoveThrottleMs) {
            resetGuideAutoClose()
            return
        }

        lastGuideMoveAt = now

        previousGuideSelectedIndex = guideSelectedIndex

        guideSelectedIndex += delta

        if (guideSelectedIndex > grid.lastIndex) {
            guideSelectedIndex = 0
        }

        if (guideSelectedIndex < 0) {
            guideSelectedIndex = grid.lastIndex
        }

        updateGuideRowState(previousGuideSelectedIndex)
        updateGuideRowState(guideSelectedIndex)

        forceGuideScrollToSelected(center = false)
        resetGuideAutoClose()
    }

    private fun selectGuideChannel() {
        if (guideSelectedIndex !in grid.indices) return

        val selectedIndex = guideSelectedIndex

        closeGuide()
        hideInfoOverlay()
        cancelRecovery()

        currentChannelIndex = selectedIndex
        guideSelectedIndex = selectedIndex
        previousGuideSelectedIndex = selectedIndex

        playCurrentChannel(resetRecovery = true)
    }

    private fun renderGuide() {
        channelGuideList.removeAllViews()
        guideRows.clear()

        grid.forEachIndexed { index, channel ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = true
                setPadding(dp(5), dp(4), dp(5), dp(4))
            }

            val selector = TextView(this).apply {
                text = ""
                setTextColor(Color.parseColor("#38BDF8"))
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    dp(14),
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            val logo = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(24)).apply {
                    marginStart = dp(3)
                    marginEnd = dp(7)
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = false
            }

            if (!channel.logo.isNullOrBlank()) {
                Glide.with(this)
                    .load(channel.logo)
                    .dontAnimate()
                    .into(logo)
            } else {
                logo.setImageDrawable(null)
            }

            val textContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
            }

            val title = TextView(this).apply {
                text = "${channel.numero}. ${channel.name}"
                setTextColor(Color.WHITE)
                textSize = 11f
                typeface = Typeface.DEFAULT
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }

            val subtitle = TextView(this).apply {
                text = channel.category ?: channel.sourceName ?: ""
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 8.5f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }

            val marker = TextView(this).apply {
                text = ""
                setTextColor(Color.parseColor("#22C55E"))
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    marginStart = dp(5)
                }
            }

            textContainer.addView(title)
            textContainer.addView(subtitle)

            row.addView(selector)
            row.addView(logo)
            row.addView(textContainer)
            row.addView(marker)

            row.setOnTouchListener { _, event ->
                if (!isGuideOpen()) return@setOnTouchListener false

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> pauseGuideAutoClose()

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> resetGuideAutoClose()
                }

                false
            }

            row.setOnClickListener {
                pauseGuideAutoClose()
                guideSelectedIndex = index
                selectGuideChannel()
            }

            channelGuideList.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(43)
                ).apply {
                    bottomMargin = dp(3)
                }
            )

            guideRows.add(
                GuideRowView(
                    row = row,
                    selector = selector,
                    title = title,
                    subtitle = subtitle,
                    marker = marker
                )
            )
        }

        updateGuideSelectionViews()
    }

    private fun updateGuideSelectionViews() {
        guideRows.forEachIndexed { index, _ ->
            updateGuideRowState(index)
        }
    }

    private fun updateGuideRowState(index: Int) {
        if (index !in guideRows.indices) return

        val guideRow = guideRows[index]
        val selected = index == guideSelectedIndex
        val current = index == currentChannelIndex

        guideRow.row.background = createGuideRowBackground(
            selected = selected,
            current = current
        )

        guideRow.selector.text = if (selected) "▶" else ""

        guideRow.title.typeface =
            if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

        guideRow.title.setTextColor(
            when {
                selected -> Color.WHITE
                current -> Color.parseColor("#E0F2FE")
                else -> Color.WHITE
            }
        )

        guideRow.subtitle.setTextColor(
            if (selected) Color.parseColor("#E0F2FE") else Color.parseColor("#CBD5E1")
        )

        guideRow.marker.text = if (current) "●" else ""
    }

    private fun createGuideRowBackground(selected: Boolean, current: Boolean): GradientDrawable {
        val color = when {
            selected -> Color.parseColor("#774F9BCF")
            current -> Color.parseColor("#3314B8A6")
            else -> Color.TRANSPARENT
        }

        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(10).toFloat()
            setColor(color)

            if (selected) {
                setStroke(dp(1), Color.parseColor("#DD7DD3FC"))
            } else if (current) {
                setStroke(dp(1), Color.parseColor("#5534D399"))
            }
        }
    }

    private fun forceGuideScrollToSelected(center: Boolean) {
        if (guideSelectedIndex !in guideRows.indices) return

        guideScrollGeneration += 1
        val thisGeneration = guideScrollGeneration

        channelGuideScroll.post {
            if (thisGeneration != guideScrollGeneration) return@post
            correctGuideScroll(center = center)
        }

        uiHandler.postDelayed({
            if (thisGeneration != guideScrollGeneration) return@postDelayed
            correctGuideScroll(center = center)
        }, 35)

        uiHandler.postDelayed({
            if (thisGeneration != guideScrollGeneration) return@postDelayed
            correctGuideScroll(center = center)
        }, 90)
    }

    private fun correctGuideScroll(center: Boolean) {
        if (guideSelectedIndex !in guideRows.indices) return

        val selectedView = channelGuideList.getChildAt(guideSelectedIndex) ?: return

        val viewportHeight = channelGuideScroll.height
        val contentHeight = channelGuideList.height

        if (viewportHeight <= 0 || contentHeight <= 0) return

        val currentScrollY = channelGuideScroll.scrollY
        val visibleTop = currentScrollY
        val visibleBottom = currentScrollY + viewportHeight

        val selectedTop = selectedView.top
        val selectedBottom = selectedView.bottom
        val selectedCenter = selectedTop + (selectedView.height / 2)

        val safeTop = dp(22)
        val safeBottom = dp(22)

        val targetScrollY = if (center) {
            selectedCenter - (viewportHeight / 2)
        } else {
            when {
                selectedTop < visibleTop + safeTop -> {
                    selectedTop - safeTop
                }

                selectedBottom > visibleBottom - safeBottom -> {
                    selectedBottom - viewportHeight + safeBottom
                }

                else -> {
                    currentScrollY
                }
            }
        }

        val maxScrollY = (contentHeight - viewportHeight).coerceAtLeast(0)
        val finalScrollY = targetScrollY.coerceIn(0, maxScrollY)

        if (finalScrollY != currentScrollY) {
            channelGuideScroll.scrollTo(0, finalScrollY)
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
                    if ((event?.repeatCount ?: 0) == 0) {
                        selectGuideChannel()
                    }
                    true
                }

                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_BACK -> {
                    closeGuide()
                    true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    resetGuideAutoClose()
                    true
                }

                else -> {
                    resetGuideAutoClose()
                    true
                }
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
        if (error is CancellationException) {
            return
        }

        Log.e("LiveTvPlayer", "$prefix: ${error.message}", error)

        if (error is SessionExpiredException) {
            goToLogin()
            return
        }

        val message = error.message ?: prefix

        if (
            message.contains("StandaloneCoroutine was cancelled", ignoreCase = true) ||
            message.contains("Job was cancelled", ignoreCase = true)
        ) {
            return
        }

        showStatus(message)
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
        hideInfoOverlay()

        uiHandler.removeCallbacks(hideInfoOverlayRunnable)
        uiHandler.removeCallbacks(hideStatusOverlayRunnable)
        uiHandler.removeCallbacks(guideAutoCloseRunnable)

        playerView.player = null
        player?.release()
        player = null
    }
}