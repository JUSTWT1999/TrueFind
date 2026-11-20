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
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.truefind.app.R
import com.truefind.app.TrueFindApp
import kotlin.math.abs
import kotlin.math.hypot

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var dismissView: View? = null

    // Pure-icon radial satellites shown after holding the bubble for 500ms
    private var backdropView: View? = null
    private var singleCircleView: View? = null
    private var scrollCircleView: View? = null
    private var radialExpanded = false
    private var hoveredMode: CaptureMode? = null

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
                if (!visible) collapseRadialMenu()
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun attachBubbleView() {
        val settings = (application as TrueFindApp).container.settingsStore
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val sizePx = dp(48)

        val bubble = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E62563EB"))
                setStroke(dp(2), Color.parseColor("#E6FFFFFF"))
            }
            elevation = dp(6).toFloat()
        }

        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble_check)
            contentDescription = "TrueFind 查核"
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(11), dp(11), dp(11), dp(11))
        }
        bubble.addView(
            icon,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (settings.bubbleOnRightSide) screenW - sizePx - dp(8) else dp(8)
            y = (screenH * settings.bubbleYFraction).toInt()
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false
        var expandedThisGesture = false

        val holdExpandRunnable = Runnable {
            if (!dragging) {
                expandedThisGesture = true
                bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                expandRadialMenu()
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
                    expandedThisGesture = false
                    hoveredMode = null
                    if (!radialExpanded) {
                        handler.postDelayed(holdExpandRunnable, HOLD_EXPAND_MS)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()

                    if (expandedThisGesture && radialExpanded) {
                        // Slide-to-select onto one of the two pure-icon circles
                        updateRadialHover(event.rawX, event.rawY, bubble)
                        return@setOnTouchListener true
                    }

                    if (!dragging && !radialExpanded && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(holdExpandRunnable)
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
                    handler.removeCallbacks(holdExpandRunnable)
                    hideDismissZone()

                    if (expandedThisGesture && radialExpanded) {
                        val chosen = hoveredMode
                        hoveredMode = null
                        if (chosen != null) {
                            collapseRadialMenu()
                            triggerCapture(chosen)
                        }
                        // If user released on the main bubble without sliding, keep the 2 icon circles open for tapping!
                        return@setOnTouchListener true
                    }

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
                            params.x = if (snapRight) screenW - sizePx - dp(8) else dp(8)
                            windowManager.updateViewLayout(bubble, params)
                            settings.bubbleOnRightSide = snapRight
                            settings.bubbleYFraction = params.y.toFloat() / screenH.toFloat()
                        }
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        if (radialExpanded) {
                            collapseRadialMenu()
                        } else {
                            triggerCapture(CaptureMode.SINGLE)
                        }
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

    private fun expandRadialMenu() {
        if (radialExpanded) return
        val bp = bubbleParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val bubbleSize = bp.width
        val satelliteSize = dp(46)
        val mainCenterX = bp.x + bubbleSize / 2
        val mainCenterY = bp.y + bubbleSize / 2
        val onRight = mainCenterX >= screenW / 2

        val offsetX = dp(64) * if (onRight) -1 else 1
        val offsetY = dp(40)

        val singleX = (mainCenterX + offsetX - satelliteSize / 2).coerceIn(dp(12), screenW - satelliteSize - dp(12))
        val singleY = (mainCenterY - offsetY - satelliteSize / 2).coerceIn(dp(36), screenH - satelliteSize - dp(36))
        val scrollX = singleX
        val scrollY = (mainCenterY + offsetY - satelliteSize / 2).coerceIn(dp(36), screenH - satelliteSize - dp(36))

        // Transparent backdrop to dismiss the 2 icon circles when tapping anywhere outside
        val backdrop = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { collapseRadialMenu() }
        }
        val backdropParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(backdrop, backdropParams)
        backdropView = backdrop

        val singleView = createIconCircle(
            iconRes = R.drawable.ic_capture_single,
            fillHex = "#F20EA5E9",
            contentDesc = "單張截圖查核",
            onClick = {
                collapseRadialMenu()
                triggerCapture(CaptureMode.SINGLE)
            }
        )
        val scrollView = createIconCircle(
            iconRes = R.drawable.ic_capture_scroll,
            fillHex = "#F29333EA",
            contentDesc = "捲動長截圖查核",
            onClick = {
                collapseRadialMenu()
                triggerCapture(CaptureMode.SCROLL_STITCH)
            }
        )

        addSatelliteView(singleView, singleX, singleY, satelliteSize)
        addSatelliteView(scrollView, scrollX, scrollY, satelliteSize)

        singleCircleView = singleView
        scrollCircleView = scrollView
        radialExpanded = true
    }

    private fun createIconCircle(
        iconRes: Int,
        fillHex: String,
        contentDesc: String,
        onClick: () -> Unit
    ): View {
        val circle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(fillHex))
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(8).toFloat()
            scaleX = 0.25f
            scaleY = 0.25f
            alpha = 0f
            setOnClickListener { onClick() }
        }
        val img = ImageView(this).apply {
            setImageResource(iconRes)
            contentDescription = contentDesc
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        circle.addView(
            img,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return circle
    }

    private fun addSatelliteView(view: View, xPos: Int, yPos: Int, sizePx: Int) {
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = xPos
            y = yPos
        }
        windowManager.addView(view, params)
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(150L)
            .setInterpolator(OvershootInterpolator(1.3f))
            .start()
    }

    private fun updateRadialHover(rawX: Float, rawY: Float, hapticSource: View) {
        val hitRadius = dp(40).toFloat()
        val loc = IntArray(2)
        val distSingle = singleCircleView?.let { v ->
            v.getLocationOnScreen(loc)
            hypot(rawX - (loc[0] + v.width / 2f), rawY - (loc[1] + v.height / 2f))
        } ?: Float.MAX_VALUE
        val distScroll = scrollCircleView?.let { v ->
            v.getLocationOnScreen(loc)
            hypot(rawX - (loc[0] + v.width / 2f), rawY - (loc[1] + v.height / 2f))
        } ?: Float.MAX_VALUE

        val nextHover = when {
            distSingle <= hitRadius && distSingle <= distScroll -> CaptureMode.SINGLE
            distScroll <= hitRadius -> CaptureMode.SCROLL_STITCH
            else -> null
        }
        if (nextHover != hoveredMode) {
            hoveredMode = nextHover
            if (nextHover != null) {
                hapticSource.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            singleCircleView?.animate()?.scaleX(if (nextHover == CaptureMode.SINGLE) 1.2f else 1f)
                ?.scaleY(if (nextHover == CaptureMode.SINGLE) 1.2f else 1f)?.setDuration(80L)?.start()
            scrollCircleView?.animate()?.scaleX(if (nextHover == CaptureMode.SCROLL_STITCH) 1.2f else 1f)
                ?.scaleY(if (nextHover == CaptureMode.SCROLL_STITCH) 1.2f else 1f)?.setDuration(80L)?.start()
        }
    }

    private fun collapseRadialMenu() {
        radialExpanded = false
        hoveredMode = null
        singleCircleView?.let { runCatching { windowManager.removeView(it) } }
        singleCircleView = null
        scrollCircleView?.let { runCatching { windowManager.removeView(it) } }
        scrollCircleView = null
        backdropView?.let { runCatching { windowManager.removeView(it) } }
        backdropView = null
    }

    private fun triggerCapture(mode: CaptureMode) {
        collapseRadialMenu()
        bubbleView?.visibility = View.INVISIBLE
        handler.postDelayed({
            startActivity(
                ScreenCaptureActivity.createIntent(
                    context = this,
                    mode = mode,
                    delayMs = ScreenCaptureActivity.DEFAULT_CAPTURE_DELAY_MS
                )
            )
        }, 150L)
    }

    private fun showDismissZone() {
        if (dismissView != null) return
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
        collapseRadialMenu()
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
        private const val HOLD_EXPAND_MS = 500L

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
