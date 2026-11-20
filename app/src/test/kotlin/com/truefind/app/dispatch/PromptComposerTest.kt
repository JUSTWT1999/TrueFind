package com.truefind.app.dispatch

import com.truefind.app.model.CapturePayload
import com.truefind.app.model.CaptureSource
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptComposerTest {

    @Test
    fun `image payload uses trimmed prompt directly`() {
        val payload = CapturePayload(
            imageFilePath = "/cache/cap_1.png",
            text = null,
            source = CaptureSource.SINGLE_SCREENSHOT
        )
        val result = PromptComposer.compose("  請查核這張圖  ", payload)
        assertEquals("請查核這張圖", result)
    }

    @Test
    fun `text payload appends captured text below separator`() {
        val payload = CapturePayload(
            imageFilePath = null,
            text = "  網傳喝冰水會傷腎  ",
            source = CaptureSource.PROCESS_TEXT
        )
        val result = PromptComposer.compose("請幫我事實查核：", payload)
        assertEquals("請幫我事實查核：\n\n---\n待查核內容：\n網傳喝冰水會傷腎", result)
    }

    @Test
    fun `blank prompt with text payload returns captured text`() {
        val payload = CapturePayload(
            imageFilePath = null,
            text = "https://example.com/post",
            source = CaptureSource.SHARE_TEXT
        )
        val result = PromptComposer.compose("   ", payload)
        assertEquals("https://example.com/post", result)
    }
}
