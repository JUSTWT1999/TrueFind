package com.truefind.app.assist

import android.app.assist.AssistStructure
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.widget.Toast
import com.truefind.app.R
import com.truefind.app.TrueFindApp
import com.truefind.app.chooser.ChooserActivity
import com.truefind.app.model.CapturePayload
import com.truefind.app.stitch.VerticalBitmapStitcher
import com.truefind.shared.model.CaptureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class TrueFindAssistService : VoiceInteractionService()

class TrueFindAssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        TrueFindAssistSession(this)
}

/**
 * Play-Store-compliant zero-dialog screen snapshot via Android's Digital Assistant API.
 * When TrueFind is selected as the device's default digital assistant, long-pressing Home/Power
 * (or swiping from a bottom corner) delivers the current screen Bitmap directly without any
 * MediaProjection confirmation dialog or app picker.
 */
class TrueFindAssistSession(context: Context) : VoiceInteractionSession(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val dispatched = AtomicBoolean(false)

    private var capturedAssistText: String? = null
    private var screenshotCallbackReceived = false

    private val fallbackTimeoutRunnable = Runnable {
        if (dispatched.compareAndSet(false, true)) {
            dispatchTextFallbackOrGuide()
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        dispatched.set(false)
        capturedAssistText = null
        screenshotCallbackReceived = false

        // Wait briefly for Android to deliver onHandleScreenshot / onHandleAssist
        mainHandler.removeCallbacks(fallbackTimeoutRunnable)
        mainHandler.postDelayed(fallbackTimeoutRunnable, SCREENSHOT_WAIT_TIMEOUT_MS)
    }

    override fun onHandleAssist(state: AssistState) {
        super.onHandleAssist(state)
        val extracted = extractFallbackText(state)
        if (!extracted.isNullOrBlank()) {
            capturedAssistText = extracted
        }
        // If screenshot already came back null, immediately fall back to extracted text
        if (screenshotCallbackReceived && !capturedAssistText.isNullOrBlank()) {
            if (dispatched.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(fallbackTimeoutRunnable)
                launchChooserWithText(capturedAssistText!!)
            }
        }
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        screenshotCallbackReceived = true

        if (screenshot == null) {
            if (!capturedAssistText.isNullOrBlank() && dispatched.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(fallbackTimeoutRunnable)
                launchChooserWithText(capturedAssistText!!)
            }
            return
        }

        if (!dispatched.compareAndSet(false, true)) return
        mainHandler.removeCallbacks(fallbackTimeoutRunnable)

        val app = context.applicationContext as TrueFindApp
        val copyForIo = screenshot.copy(Bitmap.Config.ARGB_8888, false)
        sessionScope.launch {
            val resultPath = withContext(Dispatchers.Default) {
                try {
                    if (VerticalBitmapStitcher.isSecureBlackScreen(copyForIo)) {
                        null
                    } else {
                        app.container.cacheManager.saveBitmap(copyForIo, prefix = "assist").absolutePath
                    }
                } finally {
                    copyForIo.recycle()
                }
            }

            if (resultPath != null) {
                ChooserActivity.launch(
                    context,
                    CapturePayload(
                        imageFilePath = resultPath,
                        text = null,
                        source = CaptureSource.DIGITAL_ASSISTANT,
                        frameCount = 1
                    )
                )
            } else if (!capturedAssistText.isNullOrBlank()) {
                launchChooserWithText(capturedAssistText!!)
            } else {
                Toast.makeText(context, R.string.error_secure_screen, Toast.LENGTH_LONG).show()
            }
            hide()
        }
    }

    private fun dispatchTextFallbackOrGuide() {
        val text = capturedAssistText
        if (!text.isNullOrBlank()) {
            launchChooserWithText(text)
        } else {
            Toast.makeText(context, R.string.error_assist_no_screenshot, Toast.LENGTH_LONG).show()
            hide()
        }
    }

    private fun launchChooserWithText(text: String) {
        ChooserActivity.launch(
            context,
            CapturePayload(
                imageFilePath = null,
                text = text,
                source = CaptureSource.DIGITAL_ASSISTANT
            )
        )
        hide()
    }

    private fun extractFallbackText(state: AssistState): String? {
        val webUrl = state.assistContent?.webUri?.toString()?.takeIf { it.isNotBlank() }
        val structure = state.assistStructure
        val chunks = mutableListOf<String>()
        if (webUrl != null) chunks.add(webUrl)
        if (structure != null) {
            for (i in 0 until structure.windowNodeCount) {
                collectText(structure.getWindowNodeAt(i).rootViewNode, chunks, maxChars = 1200)
            }
        }
        return chunks.joinToString("\n").trim().takeIf { it.isNotEmpty() }
    }

    private fun collectText(
        node: AssistStructure.ViewNode?,
        out: MutableList<String>,
        maxChars: Int
    ) {
        if (node == null) return
        val currentTotal = out.sumOf { it.length }
        if (currentTotal >= maxChars) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty() && text.length >= 4) {
            out.add(text)
        }
        for (i in 0 until node.childCount) {
            collectText(node.getChildAt(i), out, maxChars)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        sessionScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SCREENSHOT_WAIT_TIMEOUT_MS = 1000L
    }
}
