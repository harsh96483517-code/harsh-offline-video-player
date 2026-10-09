package com.harsh.player.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.harsh.player.R
import com.harsh.player.data.Prefs
import com.harsh.player.data.VideoRepository
import com.harsh.player.data.model.SortOrder
import com.harsh.player.data.model.VideoItem
import com.harsh.player.databinding.ActivityPlayerBinding
import com.harsh.player.util.Formatters
import kotlinx.coroutines.launch

/**
 * Cinematic playback screen.
 *
 * Playback is the priority: we use Media3 ExoPlayer with modest buffers, a
 * single player instance, hardware decoding left to the platform, and a
 * lightweight overlay that auto-hides. Resume positions are saved periodically
 * (not every frame) and on pause/stop.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var b: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private lateinit var repo: VideoRepository
    private lateinit var prefs: Prefs

    private lateinit var item: VideoItem
    private var playlist: List<VideoItem> = emptyList()
    private var playlistIndex = -1

    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = true
    private var autoHideMs = 4000L

    private val progressTick = object : Runnable {
        override fun run() {
            updateProgress()
            savePosition()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private val hideControls = Runnable { hideControls() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        repo = VideoRepository.get(this)
        prefs = Prefs(this)

        item = intent.parcelable(EXTRA_ITEM) ?: run { finish(); return }
        buildPlaylist()

        buildPlayer()
        bindUi()
        loadPreferences()
    }

    // ---- Setup ------------------------------------------------------------

    private fun buildPlaylist() {
        playlist = repo.sorted(SortOrder.NAME)
        playlistIndex = playlist.indexOfFirst { it.key == item.key }
        if (playlistIndex < 0) {
            playlist = listOf(item)
            playlistIndex = 0
        }
    }

    private fun buildPlayer() {
        // Modest buffers: enough to smooth local reads without large allocations.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()

        b.playerView.player = player
        b.playerView.setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                b.buffering.visibility =
                    if (state == Player.STATE_BUFFERING) View.VISIBLE else View.GONE
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                b.btnPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                showPlaybackError(error)
            }
        })

        playCurrent()
    }

    private fun playCurrent() {
        val resume = repo.history.resumePosition(item.key)
        val mediaItem = MediaItem.fromUri(item.uri)
        player.setMediaItem(mediaItem, if (resume > 0) resume else C.TIME_UNSET)
        player.prepare()
        player.playWhenReady = true
        repo.history.recordPlayed(item.key)
        b.title.text = item.title
        b.resolution.text = Formatters.resolution(item.width, item.height)
        handler.post(progressTick)
        scheduleAutoHide()
    }

    private fun bindUi() {
        b.btnPlayPause.setOnClickListener { togglePlay() }
        b.btnForward.setOnClickListener { seekBy(SEEK_MS) }
        b.btnBackward.setOnClickListener { seekBy(-SEEK_MS) }
        b.btnNext.setOnClickListener { playNext(1) }
        b.btnPrev.setOnClickListener { playNext(-1) }
        b.btnSettings.setOnClickListener { showSettingsPanel() }
        b.btnFavorite.setOnClickListener { toggleFavorite() }

        b.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) b.currentTime.text = Formatters.duration(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar) { handler.removeCallbacks(hideControls) }
            override fun onStopTrackingTouch(sb: android.widget.SeekBar) {
                player.seekTo(sb.progress.toLong())
                scheduleAutoHide()
            }
        })
    }

    private fun loadPreferences() {
        lifecycleScope.launch {
            prefs.autoHideControls.collect { enabled ->
                autoHideMs = if (enabled) 4000L else Long.MAX_VALUE
            }
        }
        lifecycleScope.launch {
            prefs.playbackSpeed.collect { player.setPlaybackSpeed(it) }
        }
        lifecycleScope.launch {
            prefs.aspectRatio.collect { applyAspect(it) }
        }
        lifecycleScope.launch {
            prefs.subtitleLang.collect { applyPreferredSubtitle(it) }
        }
    }

    // ---- Controls ---------------------------------------------------------

    private fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        scheduleAutoHide()
    }

    private fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(target)
        showControls()
        scheduleAutoHide()
    }

    private fun playNext(direction: Int) {
        if (playlist.isEmpty()) return
        val next = (playlistIndex + direction + playlist.size) % playlist.size
        playlistIndex = next
        item = playlist[next]
        playCurrent()
    }

    private fun toggleFavorite() {
        val nowFav = repo.history.toggleFavorite(item.key)
        b.btnFavorite.setImageResource(
            if (nowFav) R.drawable.ic_star_filled else R.drawable.ic_star
        )
    }

    private fun showControls() {
        controlsVisible = true
        b.overlay.visibility = View.VISIBLE
        b.overlay.alpha = 0f
        b.overlay.animate().alpha(1f).setDuration(150L).start()
    }

    private fun hideControls() {
        controlsVisible = false
        b.overlay.animate().alpha(0f).setDuration(150L).withEndAction {
            if (!controlsVisible) b.overlay.visibility = View.INVISIBLE
        }.start()
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(hideControls)
        if (autoHideMs != Long.MAX_VALUE) handler.postDelayed(hideControls, autoHideMs)
    }

    // ---- Progress / resume ------------------------------------------------

    private fun updateProgress() {
        val duration = player.duration
        if (duration > 0) {
            b.seekBar.max = duration.toInt()
            b.seekBar.progress = player.currentPosition.toInt()
            b.currentTime.text = Formatters.duration(player.currentPosition)
            b.totalTime.text = Formatters.duration(duration)
            b.remainingTime.text = "-" + Formatters.duration(duration - player.currentPosition)
        }
    }

    private fun savePosition() {
        val duration = player.duration
        if (duration > 0) {
            repo.history.saveResumePosition(item.key, player.currentPosition, duration)
        }
    }

    // ---- Aspect ratio / tracks -------------------------------------------

    private fun applyAspect(mode: String) {
        b.playerView.resizeMode = when (mode) {
            "fill" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            "16:9", "4:3" -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun applyPreferredSubtitle(lang: String) {
        val tracks = player.currentTracks
        if (tracks.isEmpty) return
        val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        if (groups.isEmpty()) return
        if (lang == "none") {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            return
        }
        // "auto" leaves the player's default selection in place.
        if (lang == "auto") return
        for (group in groups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                if (format.language?.startsWith(lang, ignoreCase = true) == true) {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        .build()
                    return
                }
            }
        }
    }

    private fun showSettingsPanel() {
        val options = arrayOf(
            getString(R.string.opt_speed),
            getString(R.string.opt_audio),
            getString(R.string.opt_subtitles),
            getString(R.string.opt_aspect),
            getString(R.string.opt_info)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.player_settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSpeedDialog()
                    1 -> showAudioDialog()
                    2 -> showSubtitleDialog()
                    3 -> showAspectDialog()
                    4 -> showInfoDialog()
                }
            }
            .show()
    }

    private fun showSpeedDialog() {
        val speeds = arrayOf("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x")
        val values = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        val current = values.indexOfFirst { kotlin.math.abs(it - player.playbackParameters.speed) < 0.01f }
        AlertDialog.Builder(this)
            .setTitle(R.string.opt_speed)
            .setSingleChoiceItems(speeds, current) { d, which ->
                player.setPlaybackSpeed(values[which])
                lifecycleScope.launch { prefs.setPlaybackSpeed(values[which]) }
                d.dismiss()
            }.show()
    }

    private fun showAudioDialog() {
        val tracks = player.currentTracks
        val labels = ArrayList<String>()
        val groups = ArrayList<Tracks.Group>()
        for (g in tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }) {
            for (i in 0 until g.length) {
                labels.add(g.getTrackFormat(i).label ?: g.getTrackFormat(i).language ?: "Track ${i + 1}")
            }
            groups.add(g)
        }
        if (labels.isEmpty()) { toast(R.string.no_audio_tracks); return }
        AlertDialog.Builder(this)
            .setTitle(R.string.opt_audio)
            .setItems(labels.toTypedArray()) { d, _ -> d.dismiss() }.show()
    }

    private fun showSubtitleDialog() {
        val labels = arrayOf("Off", "Auto", "English", "Hindi")
        AlertDialog.Builder(this)
            .setTitle(R.string.opt_subtitles)
            .setItems(labels) { d, which ->
                val lang = when (which) {
                    0 -> "none"; 1 -> "auto"; 2 -> "en"; else -> "hi"
                }
                applyPreferredSubtitle(lang)
                lifecycleScope.launch { prefs.setSubtitleLang(lang) }
                d.dismiss()
            }.show()
    }

    private fun showAspectDialog() {
        val labels = arrayOf("Fit", "Fill", "Zoom", "16:9", "4:3")
        val values = arrayOf("fit", "fill", "zoom", "16:9", "4:3")
        AlertDialog.Builder(this)
            .setTitle(R.string.opt_aspect)
            .setItems(labels) { d, which ->
                applyAspect(values[which])
                lifecycleScope.launch { prefs.setAspectRatio(values[which]) }
                d.dismiss()
            }.show()
    }

    private fun showInfoDialog() {
        val info = buildString {
            appendLine("${getString(R.string.field_title)}: ${item.title}")
            appendLine("${getString(R.string.field_resolution)}: ${Formatters.resolution(item.width, item.height)}")
            appendLine("${getString(R.string.field_duration)}: ${Formatters.duration(item.durationMs)}")
            appendLine("${getString(R.string.field_size)}: ${Formatters.fileSize(item.sizeBytes)}")
            appendLine("${getString(R.string.field_source)}: ${item.source.name}")
            append("${getString(R.string.field_path)}: ${item.relativePath}")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.opt_info)
            .setMessage(info)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ---- Error handling ---------------------------------------------------

    private fun showPlaybackError(error: PlaybackException) {
        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
                getString(R.string.error_decoder)
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                getString(R.string.error_missing_file)
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                getString(R.string.error_permission)
            else -> getString(R.string.error_generic)
        }
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle(R.string.error_title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.action_retry) { _, _ ->
                player.prepare()
                player.play()
            }
            .setNegativeButton(R.string.action_close) { _, _ -> finish() }
            .show()
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    // ---- Key handling -----------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_HEADSETHOOK -> {
                    if (controlsVisible) hideControls() else showControls()
                    scheduleAutoHide()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { seekBy(SEEK_MS); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND -> { seekBy(-SEEK_MS); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!controlsVisible) { seekBy(-SEEK_MS); return true }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (!controlsVisible) { seekBy(SEEK_MS); return true }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Any key press wakes the controls.
        if (!controlsVisible) { showControls(); scheduleAutoHide() }
        return super.onKeyDown(keyCode, event)
    }

    // ---- Lifecycle --------------------------------------------------------

    override fun onPause() {
        super.onPause()
        savePosition()
        player.pause()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        handler.removeCallbacks(progressTick)
        handler.removeCallbacks(hideControls)
        player.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player.release() // release playback resources promptly
    }

    companion object {
        private const val EXTRA_ITEM = "extra_item"
        private const val SEEK_MS = 10_000L
        private const val TICK_MS = 1_000L

        fun intent(context: Context, item: VideoItem): Intent =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_ITEM, item)

        private fun Intent.parcelable(key: String): VideoItem? {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                getParcelableExtra(key, VideoItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableExtra(key)
            }
        }
    }
}
