package com.harsh.player.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.harsh.player.databinding.ActivitySplashBinding
import com.harsh.player.ui.home.HomeActivity

/**
 * Minimal splash screen. Shows the HARSH brand and hands off to the home screen
 * quickly — no long animations, which matters on slow TV hardware.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, HomeActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, SPLASH_MS)
    }

    companion object {
        private const val SPLASH_MS = 900L
    }
}
