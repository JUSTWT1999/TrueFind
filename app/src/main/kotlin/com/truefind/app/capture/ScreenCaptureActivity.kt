package com.truefind.app.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class ScreenCaptureActivity : ComponentActivity() {

    private var mode: CaptureMode = CaptureMode.SINGLE
    private var delayMs: Long = 160L

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val serviceIntent = ScreenCaptureService.createStartIntent(
                    context = this,
                    resultCode = result.resultCode,
                    resultData = data,
                    mode = mode,
                    initialDelayMs = delayMs
                )
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                // User cancelled consent dialog — quietly restore floating bubble if active
                FloatingBubbleService.setBubbleVisible(this, visible = true)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modeName = intent.getStringExtra(EXTRA_MODE)
        mode = CaptureMode.entries.firstOrNull { it.name == modeName } ?: CaptureMode.SINGLE
        delayMs = intent.getLongExtra(EXTRA_DELAY_MS, 160L).coerceIn(100L, 600L)

        // Ensure floating bubble stays hidden while consent + screenshot run
        FloatingBubbleService.setBubbleVisible(this, visible = false)

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    companion object {
        private const val EXTRA_MODE = "com.truefind.extra.CAPTURE_MODE"
        private const val EXTRA_DELAY_MS = "com.truefind.extra.DELAY_MS"

        fun createIntent(
            context: Context,
            mode: CaptureMode = CaptureMode.SINGLE,
            delayMs: Long = 160L
        ): Intent = Intent(context, ScreenCaptureActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode.name)
            putExtra(EXTRA_DELAY_MS, delayMs)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
    }
}

enum class CaptureMode {
    SINGLE,
    SCROLL_STITCH
}
