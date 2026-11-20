package com.truefind.app.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class ScreenCaptureActivity : ComponentActivity() {

    private var mode: CaptureMode = CaptureMode.SINGLE
    private var delayMs: Long = DEFAULT_CAPTURE_DELAY_MS

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
                FloatingBubbleService.setBubbleVisible(this, visible = true)
            }
            finishAndRemoveTask()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modeName = intent.getStringExtra(EXTRA_MODE)
        mode = CaptureMode.entries.firstOrNull { it.name == modeName } ?: CaptureMode.SINGLE
        delayMs = intent.getLongExtra(EXTRA_DELAY_MS, DEFAULT_CAPTURE_DELAY_MS).coerceIn(250L, 800L)

        FloatingBubbleService.setBubbleVisible(this, visible = false)

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Defaults Android 14/15 system dialog to single-app capture while remaining Play compliant.
            projectionManager.createScreenCaptureIntent(
                MediaProjectionConfig.createConfigForUserChoice()
            )
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        projectionLauncher.launch(captureIntent)
    }

    companion object {
        private const val EXTRA_MODE = "com.truefind.extra.CAPTURE_MODE"
        private const val EXTRA_DELAY_MS = "com.truefind.extra.DELAY_MS"
        const val DEFAULT_CAPTURE_DELAY_MS = 360L

        fun createIntent(
            context: Context,
            mode: CaptureMode = CaptureMode.SINGLE,
            delayMs: Long = DEFAULT_CAPTURE_DELAY_MS
        ): Intent = Intent(context, ScreenCaptureActivity::class.java).apply {
            putExtra(EXTRA_MODE, mode.name)
            putExtra(EXTRA_DELAY_MS, delayMs)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
    }
}

enum class CaptureMode {
    SINGLE,
    SCROLL_STITCH
}
