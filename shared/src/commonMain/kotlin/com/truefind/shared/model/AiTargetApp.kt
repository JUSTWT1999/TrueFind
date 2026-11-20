package com.truefind.shared.model

data class AiTargetApp(
    val id: String,
    val displayName: String,
    val badgeText: String,
    val androidPackageName: String,
    val iosUrlScheme: String,
    val webPromptBaseUrl: String?,
    val accentColorHex: Long,
    val isInstalled: Boolean = false,
    val supportsImageShare: Boolean = false,
    val supportsTextShare: Boolean = false
) {
    fun supports(hasImage: Boolean): Boolean =
        isInstalled && if (hasImage) supportsImageShare else supportsTextShare
}

object KnownAiTargets {
    val ALL: List<AiTargetApp> = listOf(
        AiTargetApp(
            id = "chatgpt",
            displayName = "ChatGPT",
            badgeText = "GPT",
            androidPackageName = "com.openai.chatgpt",
            iosUrlScheme = "chatgpt://",
            webPromptBaseUrl = "https://chatgpt.com/?q=",
            accentColorHex = 0xFF10A37F
        ),
        AiTargetApp(
            id = "gemini",
            displayName = "Gemini",
            badgeText = "✦",
            androidPackageName = "com.google.android.apps.bard",
            iosUrlScheme = "googleapp://",
            webPromptBaseUrl = "https://gemini.google.com/app",
            accentColorHex = 0xFF4F6DF5
        ),
        AiTargetApp(
            id = "perplexity",
            displayName = "Perplexity",
            badgeText = "P",
            androidPackageName = "ai.perplexity.app.android",
            iosUrlScheme = "perplexity://",
            webPromptBaseUrl = "https://www.perplexity.ai/search?q=",
            accentColorHex = 0xFF1F7A8C
        ),
        AiTargetApp(
            id = "claude",
            displayName = "Claude",
            badgeText = "C",
            androidPackageName = "com.anthropic.claude",
            iosUrlScheme = "claude://",
            webPromptBaseUrl = "https://claude.ai/new?q=",
            accentColorHex = 0xFFD97757
        ),
        AiTargetApp(
            id = "grok",
            displayName = "Grok",
            badgeText = "X",
            androidPackageName = "ai.x.grok",
            iosUrlScheme = "grok://",
            webPromptBaseUrl = "https://grok.com/",
            accentColorHex = 0xFF27272A
        )
    )
}
