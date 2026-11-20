package com.truefind.app.capture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.truefind.app.TrueFindApp
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var dismissView: View? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                (application as TrueFindApp).container.settingsStore.bubbleEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_VISIBLE -> {
                val visible = intent.getBooleanExtra(EXTRA_VISIBLE, true)
                bubbleView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
                return START_STICKY
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            (application as TrueFindApp).container.settingsStore.bubbleEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }

        val stopIntent = Intent(this, FloatingBubbleService::class.java).apply { action = ACTION_STOP }
        val notification = NotificationHelper.buildBubbleNotification(this, stopIntent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIF_ID_BUBBLE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.NOTIF_ID_BUBBLE, notification)
        }

        if (bubbleView == null) {
            attachBubbleView()
        }
        return START_STICKY
    }

    private fun attachBubbleView() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val settings = (application as TrueFindApp).container.settingsStore

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val sizePx = dp(50)

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#DC2563EB"))
                setStroke(dp(2), Color.parseColor("#E6FFFFFF"))
            }
            elevation = dp(6).toFloat()
        }

        val icon = TextView(this).apply {
            text = "✓"
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val label = TextView(this).apply {
            text = "查核"
            textSize = 9f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        bubble.addView(icon)
        bubble.addView(label)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (settings.bubbleOnRightSide) screenW - sizePx - dp(6) else dp(6)
            y = (screenH * settings.bubbleYFraction).toInt()
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false
        var longPressTriggered = false

        val longPressRunnable = Runnable {
            if (!dragging) {
                longPressTriggered = true
                bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                triggerCapture(CaptureMode.SCROLL_STITCH)
            }
        }

        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    dragging = false
                    longPressTriggered = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable)
                        showDismissZone()
                    }
                    if (dragging) {
                        params.x = (initialX + dx).coerceIn(0, screenW - sizePx)
                        params.y = (initialY + dy).coerceIn(dp(24), screenH - sizePx - dp(16))
                        windowManager.updateViewLayout(bubble, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    hideDismissZone()
                    if (dragging) {
                        val centerY = params.y + sizePx / 2
                        val centerX = params.x + sizePx / 2
                        val inDismissZone = centerY > screenH - dp(110) &&
                            abs(centerX - screenW / 2) < dp(90)
                        if (inDismissZone) {
                            settings.bubbleEnabled = false
                            stopSelf()
                        } else {
                            val snapRight = centerX >= screenW / 2
                            params.x = if (snapRight) screenW - sizePx - dp(6) else dp(6)
                            windowManager.updateViewLayout(bubble, params)
                            settings.bubbleOnRightSide = snapRight
                            settings.bubbleYFraction = params.y.toFloat() / screenH.toFloat()
                        }
                    } else if (!longPressTriggered && event.actionMasked == MotionEvent.ACTION_UP) {
                        triggerCapture(CaptureMode.SINGLE)
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(bubble, params)
        bubbleView = bubble
        bubbleParams = params
    }

    private fun triggerCapture(mode: CaptureMode) {
        bubbleView?.visibility = View.INVISIBLE
        handler.postDelayed({
            startActivity(ScreenCaptureActivity.createIntent(this, mode = mode, delayMs = 150L))
        }, 150L)
    }

    private fun showDismissZone() {
        if (dismissView != null) return
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val target = TextView(this).apply {
            text = "✕ 拖曳至此關閉"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CCDC2626"))
                cornerRadius = dp(24).toFloat()
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(44)
        }
        windowManager.addView(target, params)
        dismissView = target
    }

    private fun hideDismissZone() {
        val view = dismissView ?: return
        dismissView = null
        runCatching { windowManager.removeView(view) }
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)
        hideDismissZone()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
        bubbleParams = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_STOP = "com.truefind.action.STOP_BUBBLE"
        private const val ACTION_SET_VISIBLE = "com.truefind.action.SET_BUBBLE_VISIBLE"
        private const val EXTRA_VISIBLE = "com.truefind.extra.VISIBLE"
        private const val LONG_PRESS_THRESHOLD_MS = 400L

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, FloatingBubbleService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBubbleService::class.java))
        }

        fun setBubbleVisible(context: Context, visible: Boolean) {
            if (!isRunning) return
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_SET_VISIBLE
                putExtra(EXTRA_VISIBLE, visible)
            }
            runCatching { context.startService(intent) }
        }
    }
}
