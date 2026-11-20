package com.truefind.app.dispatch

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.truefind.app.R
import com.truefind.app.data.CaptureCacheManager
import com.truefind.app.model.CapturePayload
import com.truefind.shared.dispatch.PromptComposer
import com.truefind.shared.model.AiTargetApp
import java.io.File

class AiDispatcher(
    private val context: Context,
    private val cacheManager: CaptureCacheManager
) {

    fun dispatchToTarget(
        target: AiTargetApp,
        payload: CapturePayload,
        promptBody: String
    ): Boolean {
        val finalPrompt = PromptComposer.compose(promptBody, payload.text, payload.hasImage)
        copyPromptToClipboard(finalPrompt)

        val intent = buildSendIntent(payload, finalPrompt, target.androidPackageName) ?: return false
        return try {
            Toast.makeText(
                context,
                context.getString(R.string.toast_dispatched_template, target.displayName),
                Toast.LENGTH_LONG
            ).show()
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            dispatchWithSystemChooser(payload, promptBody)
        } catch (_: SecurityException) {
            dispatchWithSystemChooser(payload, promptBody)
        }
    }

    fun dispatchWithSystemChooser(
        payload: CapturePayload,
        promptBody: String
    ): Boolean {
        val finalPrompt = PromptComposer.compose(promptBody, payload.text, payload.hasImage)
        copyPromptToClipboard(finalPrompt)

        val baseIntent = buildSendIntent(payload, finalPrompt, targetPackage = null) ?: return false
        val chooser = Intent.createChooser(baseIntent, context.getString(R.string.app_name)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (payload.hasImage) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return runCatching {
            Toast.makeText(context, R.string.toast_prompt_copied, Toast.LENGTH_SHORT).show()
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    private fun buildSendIntent(
        payload: CapturePayload,
        finalPrompt: String,
        targetPackage: String?
    ): Intent? {
        val imagePath = payload.imageFilePath
        if (!imagePath.isNullOrBlank()) {
            val file = File(imagePath)
            if (!file.exists()) return null
            val uri = cacheManager.getShareUri(file)

            if (targetPackage != null) {
                runCatching {
                    context.grantUriPermission(
                        targetPackage,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }

            return Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                if (targetPackage != null) setPackage(targetPackage)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, finalPrompt)
                clipData = ClipData.newRawUri("TrueFind Capture", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (targetPackage != null) setPackage(targetPackage)
            putExtra(Intent.EXTRA_TEXT, finalPrompt)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun copyPromptToClipboard(prompt: String) {
        if (prompt.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("TrueFind Fact-Check Prompt", prompt))
    }
}
