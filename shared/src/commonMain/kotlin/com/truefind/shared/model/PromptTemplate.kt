package com.truefind.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PromptTemplate(
    val id: String,
    val title: String,
    val body: String,
    val isBuiltIn: Boolean = false
)

object DefaultPromptTemplates {
    val ALL: List<PromptTemplate> = listOf(
        PromptTemplate(
            id = "builtin_fact_check",
            title = "事實查核",
            body = "請幫我對以下內容進行事實查核：1) 列出主要宣稱；2) 逐項查證真偽並附上可靠來源或依據；3) 給出簡短總結（可信／部分錯誤／不實／缺乏證據）。請用繁體中文回答。",
            isBuiltIn = true
        ),
        PromptTemplate(
            id = "builtin_original_source",
            title = "找原始出處",
            body = "請幫我追查這段說法、數據或圖片最早的原始出處、發布時間與完整脈絡，確認是否有移花接木或舊聞重炒。請用繁體中文回答。",
            isBuiltIn = true
        ),
        PromptTemplate(
            id = "builtin_logic_bias",
            title = "查邏輯與偏誤",
            body = "請分析這段內容的論述結構，指出其中是否有斷章取義、偷換概念、以偏概全、誇大數據或情緒操弄，並補充平衡觀點。請用繁體中文回答。",
            isBuiltIn = true
        ),
        PromptTemplate(
            id = "builtin_translate_verify",
            title = "翻譯＋重點查證",
            body = "請先將這段外文內容翻譯成繁體中文重點摘要，接著針對核心宣稱進行事實查核並標註可信度。",
            isBuiltIn = true
        )
    )
}
