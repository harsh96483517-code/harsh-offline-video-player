package com.harsh.player.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.harsh.player.R
import com.harsh.player.data.Prefs
import com.harsh.player.data.VideoRepository
import com.harsh.player.databinding.ActivitySettingsBinding
import com.harsh.player.util.DeviceCapabilities
import com.harsh.player.util.ThumbnailLoader
import kotlinx.coroutines.launch

/** Settings screen. All changes are written through DataStore. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding
    private lateinit var prefs: Prefs
    private lateinit var repo: VideoRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        prefs = Prefs(this)
        repo = VideoRepository.get(this)

        b.lowRamNote.visibility =
            if (DeviceCapabilities.isLowRamDevice) View.VISIBLE else View.GONE
        b.deviceInfo.text = getString(
            R.string.device_info,
            DeviceCapabilities.androidVersionLabel(),
            DeviceCapabilities.totalRamMegabytes
        )

        bindResume()
        bindAutoHide()
        bindLowRam()
        bindSubtitleSize()
        bindActions()
    }

    private fun bindResume() {
        lifecycleScope.launch {
            prefs.resumePlayback.collect { b.switchResume.isChecked = it }
        }
        b.switchResume.setOnCheckedChangeListener { _, v ->
            lifecycleScope.launch { prefs.setResumePlayback(v) }
        }
    }

    private fun bindAutoHide() {
        lifecycleScope.launch {
            prefs.autoHideControls.collect { b.switchAutoHide.isChecked = it }
        }
        b.switchAutoHide.setOnCheckedChangeListener { _, v ->
            lifecycleScope.launch { prefs.setAutoHideControls(v) }
        }
    }

    private fun bindLowRam() {
        lifecycleScope.launch {
            prefs.lowRamMode.collect { b.switchLowRam.isChecked = it }
        }
        b.switchLowRam.setOnCheckedChangeListener { _, v ->
            lifecycleScope.launch {
                prefs.setLowRamMode(v)
                if (v) ThumbnailLoader.trimTo(2 * 1024) // shrink cache immediately
            }
        }
    }

    private fun bindSubtitleSize() {
        b.seekSubtitleSize.max = 48 - 12
        lifecycleScope.launch {
            prefs.subtitleSize.collect { size ->
                b.seekSubtitleSize.progress = (size - 12).coerceIn(0, 36)
                b.subtitleSizeLabel.text = getString(R.string.subtitle_size_value, size)
            }
        }
        b.seekSubtitleSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                b.subtitleSizeLabel.text = getString(R.string.subtitle_size_value, progress + 12)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {
                lifecycleScope.launch { prefs.setSubtitleSize(sb.progress + 12) }
            }
        })
    }

    private fun bindActions() {
        b.rowClearHistory.setOnClickListener {
            repo.history.clearHistory()
            toast(R.string.history_cleared)
        }
        b.rowClearCache.setOnClickListener {
            ThumbnailLoader.clear()
            cacheDir.resolve("thumbnails").deleteRecursively()
            toast(R.string.cache_cleared)
        }
        b.rowPermissions.setOnClickListener {
            startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                }
            )
        }
        b.rowAbout.setOnClickListener {
            startActivity(Intent(this, com.harsh.player.ui.about.AboutActivity::class.java))
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}
