package com.truefind.app.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.truefind.app.R
import com.truefind.app.TrueFindApp
import com.truefind.app.chooser.ChooserActivity
import com.truefind.app.model.CapturePayload
import com.truefind.shared.model.CaptureSource
import com.truefind.app.stitch.VerticalBitmapStitcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureWidth = 0
    private var captureHeight = 0
    private var captureDensityDpi = 0

    private var captureMode: CaptureMode = CaptureMode.SINGLE
    private val capturedFrames = mutableListOf<Bitmap>()
    private val isFinishing = AtomicBoolean(false)
    private val isGrabbingFrame = AtomicBoolean(false)

    private var scrollHudView: View? = null
    private var addPageBtn: TextView? = null
    private var doneBtn: TextView? = null

    private val inactivityTimeoutRunnable = Runnable {
        if (capturedFrames.isNotEmpty()) {
            finishAndStitchFrames()
        } else {
            abortSession(showError = false)
        }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post {
                if (capturedFrames.isNotEmpty() && !isFinishing.get()) {
                    finishAndStitchFrames()
                } else {
                    abortSession(showError = false)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = getParcelableIntent(intent, EXTRA_RESULT_DATA)
        if (resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val modeName = intent?.getStringExtra(EXTRA_MODE)
        captureMode = CaptureMode.entries.firstOrNull { it.name == modeName } ?: CaptureMode.SINGLE
        val initialDelayMs = intent?.getLongExtra(EXTRA_DELAY_MS, 160L) ?: 160L

        // Android 14+ requires startForeground with MEDIA_PROJECTION type BEFORE getMediaProjection()
        val notification = NotificationHelper.buildCaptureNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIF_ID_CAPTURE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NotificationHelper.NOTIF_ID_CAPTURE, notification)
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = runCatching { manager.getMediaProjection(resultCode, resultData) }.getOrNull()
        if (projection == null) {
            abortSession(showError = true)
            return START_NOT_STICKY
        }

        mediaProjection = projection
        projection.registerCallback(projectionCallback, mainHandler)

        setupVirtualDisplay(projection)

        // If scroll mode requested without overlay permission, gracefully fall back to single capture
        if (captureMode == CaptureMode.SCROLL_STITCH && !Settings.canDrawOverlays(this)) {
            captureMode = CaptureMode.SINGLE
        }

        mainHandler.postDelayed({
            grabNextFrame()
        }, initialDelayMs)

        return START_NOT_STICKY
    }

    private fun setupVirtualDisplay(projection: MediaProjection) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            captureWidth = bounds.width()
            captureHeight = bounds.height()
            captureDensityDpi = resources.displayMetrics.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            captureWidth = metrics.widthPixels
            captureHeight = metrics.heightPixels
            captureDensityDpi = metrics.densityDpi
        }

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection.createVirtualDisplay(
            "TrueFindCapture",
            captureWidth,
            captureHeight,
            captureDensityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            mainHandler
        )
    }

    private fun grabNextFrame(attempt: Int = 0) {
        if (isFinishing.get()) return
        val reader = imageReader ?: run {
            abortSession(showError = true)
            return
        }

        val image = reader.acquireLatestImage()
        if (image == null) {
            if (attempt < MAX_FRAME_RETRIES) {
                mainHandler.postDelayed({ grabNextFrame(attempt + 1) }, 60L)
            } else {
                isGrabbingFrame.set(false)
                if (capturedFrames.isEmpty()) {
                    abortSession(showError = true)
                } else {
                    showOrUpdateScrollHud()
                }
            }
            return
        }

        val bitmap = try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * captureWidth
            val paddedWidth = captureWidth + rowPadding / pixelStride
            val raw = Bitmap.createBitmap(paddedWidth, captureHeight, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buffer)
            if (paddedWidth == captureWidth) {
                raw
            } else {
                val cropped = Bitmap.createBitmap(raw, 0, 0, captureWidth, captureHeight)
                raw.recycle()
                cropped
            }
        } finally {
            image.close()
        }

        isGrabbingFrame.set(false)

        if (VerticalBitmapStitcher.isSecureBlackScreen(bitmap)) {
            bitmap.recycle()
            Toast.makeText(this, R.string.error_secure_screen, Toast.LENGTH_LONG).show()
            if (capturedFrames.isEmpty()) {
                abortSession(showError = false)
            } else {
                finishAndStitchFrames()
            }
            return
        }

        capturedFrames.add(bitmap)

        when (captureMode) {
            CaptureMode.SINGLE -> finishAndStitchFrames()
            CaptureMode.SCROLL_STITCH -> {
                if (capturedFrames.size >= MAX_SCROLL_FRAMES) {
                    finishAndStitchFrames()
                } else {
                    resetInactivityTimer()
                    showOrUpdateScrollHud()
                }
            }
        }
    }

    private fun showOrUpdateScrollHud() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (scrollHudView == null) {
            val density = resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E618181B"))
                    cornerRadius = dp(24).toFloat()
                    setStroke(dp(1), Color.parseColor("#3F3F46"))
                }
                elevation = dp(8).toFloat()
            }

            val addBtn = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2563EB"))
                    cornerRadius = dp(18).toFloat()
                }
                setOnClickListener {
                    if (isGrabbingFrame.compareAndSet(false, true)) {
                        resetInactivityTimer()
                        scrollHudView?.visibility = View.INVISIBLE
                        mainHandler.postDelayed({ grabNextFrame() }, 140L)
                    }
                }
            }

            val finishBtn = TextView(this).apply {
                text = "✓ 完成拼接"
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#16A34A"))
                    cornerRadius = dp(18).toFloat()
                }
                setOnClickListener { finishAndStitchFrames() }
            }

            val cancelBtn = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor("#D4D4D8"))
                textSize = 14f
                setPadding(dp(10), dp(8), dp(8), dp(8))
                setOnClickListener { abortSession(showError = false) }
            }

            val margin = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }

            container.addView(addBtn)
            container.addView(finishBtn, margin)
            container.addView(cancelBtn, margin)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(40)
            }

            wm.addView(container, params)
            scrollHudView = container
            addPageBtn = addBtn
            doneBtn = finishBtn
        }

        scrollHudView?.visibility = View.VISIBLE
        addPageBtn?.text = "+ 擷取下一頁 (${capturedFrames.size}/$MAX_SCROLL_FRAMES)"
        doneBtn?.text = if (capturedFrames.size == 1) "✓ 僅送此頁" else "✓ 完成拼接 (${capturedFrames.size}頁)"
    }

    private fun removeScrollHud() {
        val view = scrollHudView ?: return
        scrollHudView = null
        addPageBtn = null
        doneBtn = null
        runCatching {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        }
    }

    private fun resetInactivityTimer() {
        mainHandler.removeCallbacks(inactivityTimeoutRunnable)
        mainHandler.postDelayed(inactivityTimeoutRunnable, SCROLL_INACTIVITY_TIMEOUT_MS)
    }

    private fun finishAndStitchFrames() {
        if (!isFinishing.compareAndSet(false, true)) return
        mainHandler.removeCallbacks(inactivityTimeoutRunnable)
        removeScrollHud()
        releaseProjectionImmediately()

        val framesSnapshot = capturedFrames.toList()
        capturedFrames.clear()

        if (framesSnapshot.isEmpty()) {
            FloatingBubbleService.setBubbleVisible(this, visible = true)
            stopSelf()
            return
        }

        val statusBarH = getSystemBarHeight("status_bar_height")
        val navBarH = getSystemBarHeight("navigation_bar_height")
        val source = if (framesSnapshot.size > 1) {
            CaptureSource.SCROLL_STITCH
        } else {
            CaptureSource.SINGLE_SCREENSHOT
        }
        val frameCount = framesSnapshot.size
        val cacheManager = (application as TrueFindApp).container.cacheManager

        serviceScope.launch {
            val savedFile = withContext(Dispatchers.Default) {
                try {
                    val stitched = VerticalBitmapStitcher.stitch(
                        frames = framesSnapshot,
                        statusBarHeightPx = statusBarH,
                        navBarHeightPx = navBarH
                    )
                    try {
                        cacheManager.saveBitmap(stitched, prefix = if (frameCount > 1) "stitch" else "cap")
                    } finally {
                        stitched.recycle()
                    }
                } finally {
                    framesSnapshot.forEach { it.recycle() }
                }
            }

            // Keep floating bubble hidden while ChooserActivity is visible; ChooserActivity restores it on exit.
            ChooserActivity.launch(
                this@ScreenCaptureService,
                CapturePayload(
                    imageFilePath = savedFile.absolutePath,
                    text = null,
                    source = source,
                    frameCount = frameCount
                )
            )
            stopSelf()
        }
    }

    private fun abortSession(showError: Boolean) {
        if (!isFinishing.compareAndSet(false, true)) return
        mainHandler.removeCallbacks(inactivityTimeoutRunnable)
        removeScrollHud()
        releaseProjectionImmediately()
        capturedFrames.forEach { it.recycle() }
        capturedFrames.clear()
        if (showError) {
            Toast.makeText(this, R.string.error_capture_failed, Toast.LENGTH_SHORT).show()
        }
        FloatingBubbleService.setBubbleVisible(this, visible = true)
        stopSelf()
    }

    private fun releaseProjectionImmediately() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        }
        mediaProjection = null
    }

    private fun getSystemBarHeight(resourceName: String): Int {
        val resId = resources.getIdentifier(resourceName, "dimen", "android")
        return if (resId > 0) resources.getDimensionPixelSize(resId) else 0
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        removeScrollHud()
        releaseProjectionImmediately()
        capturedFrames.forEach { it.recycle() }
        capturedFrames.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun getParcelableIntent(intent: Intent?, key: String): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }

    companion object {
        private const val EXTRA_RESULT_CODE = "com.truefind.extra.RESULT_CODE"
        private const val EXTRA_RESULT_DATA = "com.truefind.extra.RESULT_DATA"
        private const val EXTRA_MODE = "com.truefind.extra.CAPTURE_MODE"
        private const val EXTRA_DELAY_MS = "com.truefind.extra.DELAY_MS"

        const val MAX_SCROLL_FRAMES = 5
        private const val MAX_FRAME_RETRIES = 15
        private const val SCROLL_INACTIVITY_TIMEOUT_MS = 60_000L

        fun createStartIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent,
            mode: CaptureMode,
            initialDelayMs: Long
        ): Intent = Intent(context, ScreenCaptureService::class.java).apply {
            putExtra(EXTRA_RESULT_CODE, resultCode)
            putExtra(EXTRA_RESULT_DATA, resultData)
            putExtra(EXTRA_MODE, mode.name)
            putExtra(EXTRA_DELAY_MS, initialDelayMs)
        }
    }
}
