package com.truefind.app.model

data class AiTargetApp(
    val id: String,
    val displayName: String,
    val badgeText: String,
    val packageName: String,
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
            packageName = "com.openai.chatgpt",
            accentColorHex = 0xFF10A37F
        ),
        AiTargetApp(
            id = "gemini",
            displayName = "Gemini",
            badgeText = "✦",
            packageName = "com.google.android.apps.bard",
            accentColorHex = 0xFF4F6DF5
        ),
        AiTargetApp(
            id = "perplexity",
            displayName = "Perplexity",
            badgeText = "P",
            packageName = "ai.perplexity.app.android",
            accentColorHex = 0xFF1F7A8C
        ),
        AiTargetApp(
            id = "claude",
            displayName = "Claude",
            badgeText = "C",
            packageName = "com.anthropic.claude",
            accentColorHex = 0xFFD97757
        ),
        AiTargetApp(
            id = "grok",
            displayName = "Grok",
            badgeText = "X",
            packageName = "ai.x.grok",
            accentColorHex = 0xFF27272A
        )
    )
}
