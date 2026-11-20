package com.truefind.shared.dispatch

object PromptComposer {
    fun compose(promptBody: String, capturedText: String?, hasImage: Boolean): String {
        val cleanedPrompt = promptBody.trim()
        val cleanedText = capturedText?.trim().orEmpty()
        return when {
            hasImage && cleanedText.isEmpty() -> cleanedPrompt
            cleanedText.isNotEmpty() && cleanedPrompt.isNotEmpty() ->
                "$cleanedPrompt\n\n---\n待查核內容：\n$cleanedText"
            cleanedText.isNotEmpty() -> cleanedText
            else -> cleanedPrompt
        }
    }
}
