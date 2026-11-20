package com.truefind.app.dispatch

import com.truefind.app.model.CapturePayload

object PromptComposer {
    fun compose(promptBody: String, payload: CapturePayload): String {
        val cleanedPrompt = promptBody.trim()
        val capturedText = payload.text?.trim().orEmpty()
        return when {
            payload.hasImage && capturedText.isEmpty() -> cleanedPrompt
            capturedText.isNotEmpty() && cleanedPrompt.isNotEmpty() ->
                "$cleanedPrompt\n\n---\n待查核內容：\n$capturedText"
            capturedText.isNotEmpty() -> capturedText
            else -> cleanedPrompt
        }
    }
}
