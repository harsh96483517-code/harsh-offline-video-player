package com.harsh.player.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.harsh.player.BuildConfig
import com.harsh.player.R
import com.harsh.player.databinding.ActivityAboutBinding
import com.harsh.player.util.DeviceCapabilities

/** About HARSH — brand, signature, tagline and build/device information. */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        b.device.text = DeviceCapabilities.androidVersionLabel()
        b.signature.text = getString(R.string.brand_signature)
        b.tagline.text = getString(R.string.brand_tagline)
    }
}
