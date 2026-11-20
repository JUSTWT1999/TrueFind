package com.truefind.shared.dispatch

import kotlin.test.Test
import kotlin.test.assertEquals

class PromptComposerTest {

    @Test
    fun imageOnlyUsesTrimmedPrompt() {
        assertEquals("請查核這張圖", PromptComposer.compose("  請查核這張圖  ", capturedText = null, hasImage = true))
    }

    @Test
    fun textPayloadAppendsSeparatorAndText() {
        assertEquals(
            "請幫我事實查核：\n\n---\n待查核內容：\n網傳喝冰水會傷腎",
            PromptComposer.compose("請幫我事實查核：", capturedText = "  網傳喝冰水會傷腎  ", hasImage = false)
        )
    }

    @Test
    fun blankPromptReturnsCapturedText() {
        assertEquals(
            "https://example.com/post",
            PromptComposer.compose("   ", capturedText = "https://example.com/post", hasImage = false)
        )
    }
}
