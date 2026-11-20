package com.truefind.app.capture

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import com.truefind.app.R
import com.truefind.app.TrueFindApp
import com.truefind.app.chooser.ChooserActivity
import com.truefind.app.model.CapturePayload
import com.truefind.app.model.CaptureSource

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        finish()
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        if (incoming == null) return
        val cacheManager = (application as TrueFindApp).container.cacheManager

        val payload: CapturePayload? = when (incoming.action) {
            Intent.ACTION_PROCESS_TEXT -> {
                val selected = incoming.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
                    ?.toString()
                    ?.trim()
                if (!selected.isNullOrEmpty()) {
                    CapturePayload(
                        imageFilePath = null,
                        text = selected,
                        source = CaptureSource.PROCESS_TEXT
                    )
                } else null
            }

            Intent.ACTION_SEND -> {
                val streamUri = getParcelableUri(incoming, Intent.EXTRA_STREAM)
                val extraText = incoming.getStringExtra(Intent.EXTRA_TEXT)?.trim()?.takeIf { it.isNotEmpty() }

                if (streamUri != null && (incoming.type?.startsWith("image/") == true)) {
                    val savedFile = runCatching {
                        contentResolver.openInputStream(streamUri)?.use { input ->
                            cacheManager.copyFromInputStream(input, prefix = "shared")
                        }
                    }.getOrNull()

                    if (savedFile != null) {
                        CapturePayload(
                            imageFilePath = savedFile.absolutePath,
                            text = extraText,
                            source = CaptureSource.SHARE_IMAGE
                        )
                    } else {
                        Toast.makeText(this, R.string.error_capture_failed, Toast.LENGTH_SHORT).show()
                        null
                    }
                } else if (extraText != null) {
                    CapturePayload(
                        imageFilePath = null,
                        text = extraText,
                        source = CaptureSource.SHARE_TEXT
                    )
                } else null
            }

            else -> null
        }

        if (payload != null) {
            ChooserActivity.launch(this, payload)
        }
    }

    private fun getParcelableUri(intent: Intent, key: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
    }
}
