package com.truefind.app.model

import android.content.Intent

enum class CaptureSource {
    SINGLE_SCREENSHOT,
    SCROLL_STITCH,
    SHARE_IMAGE,
    SHARE_TEXT,
    PROCESS_TEXT
}

data class CapturePayload(
    val imageFilePath: String?,
    val text: String?,
    val source: CaptureSource,
    val frameCount: Int = 1
) {
    val hasImage: Boolean get() = !imageFilePath.isNullOrBlank()
    val hasText: Boolean get() = !text.isNullOrBlank()

    fun writeToIntent(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_IMAGE_PATH, imageFilePath)
        putExtra(EXTRA_TEXT, text)
        putExtra(EXTRA_SOURCE, source.name)
        putExtra(EXTRA_FRAME_COUNT, frameCount)
    }

    companion object {
        private const val EXTRA_IMAGE_PATH = "com.truefind.extra.IMAGE_PATH"
        private const val EXTRA_TEXT = "com.truefind.extra.TEXT"
        private const val EXTRA_SOURCE = "com.truefind.extra.SOURCE"
        private const val EXTRA_FRAME_COUNT = "com.truefind.extra.FRAME_COUNT"

        fun fromIntent(intent: Intent?): CapturePayload? {
            if (intent == null) return null
            val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)?.takeIf { it.isNotBlank() }
            val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            if (imagePath == null && text == null) return null

            val sourceName = intent.getStringExtra(EXTRA_SOURCE)
            val source = CaptureSource.entries.firstOrNull { it.name == sourceName }
                ?: if (imagePath != null) CaptureSource.SHARE_IMAGE else CaptureSource.SHARE_TEXT
            val frameCount = intent.getIntExtra(EXTRA_FRAME_COUNT, 1).coerceAtLeast(1)

            return CapturePayload(
                imageFilePath = imagePath,
                text = text,
                source = source,
                frameCount = frameCount
            )
        }
    }
}
