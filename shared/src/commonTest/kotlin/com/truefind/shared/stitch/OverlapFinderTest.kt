package com.truefind.shared.stitch

import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OverlapFinderTest {

    private fun makeVirtualDocument(width: Int, totalRows: Int): ByteArray {
        val doc = ByteArray(width * totalRows)
        for (y in 0 until totalRows) {
            for (x in 0 until width) {
                val wave = ((sin(y * 0.37 + x * 0.13) + sin(y * 0.11 - x * 0.29)) * 60 + 128).toInt()
                val stripe = if ((y / 4 + x / 7) % 3 == 0) 40 else -40
                doc[y * width + x] = (wave + stripe).coerceIn(0, 255).toByte()
            }
        }
        return doc
    }

    private fun sliceFrameWithStaticChrome(
        doc: ByteArray,
        width: Int,
        frameHeight: Int,
        scrollOffset: Int,
        topBarRows: Int,
        bottomBarRows: Int
    ): ByteArray {
        val frame = ByteArray(width * frameHeight)
        for (y in 0 until frameHeight) {
            for (x in 0 until width) {
                frame[y * width + x] = when {
                    y < topBarRows -> 20.toByte()
                    y >= frameHeight - bottomBarRows -> 230.toByte()
                    else -> doc[(scrollOffset + (y - topBarRows)) * width + x]
                }
            }
        }
        return frame
    }

    @Test
    fun detectsExactOverlapDespiteStaticBars() {
        val width = 60
        val frameHeight = 120
        val topBar = 16
        val bottomBar = 16
        val contentHeight = frameHeight - topBar - bottomBar
        val scrollDelta = 52

        val doc = makeVirtualDocument(width, 300)
        val prev = sliceFrameWithStaticChrome(doc, width, frameHeight, 0, topBar, bottomBar)
        val next = sliceFrameWithStaticChrome(doc, width, frameHeight, scrollDelta, topBar, bottomBar)

        val match = OverlapFinder.findBestOverlap(prev, next, width, frameHeight, topBar, bottomBar)

        assertTrue(match.isConfident)
        assertFalse(match.isDuplicateFrame)
        assertEquals(topBar + (contentHeight - scrollDelta), match.nextNewContentStartRow)
        assertTrue(match.madScore < 0.01)
    }

    @Test
    fun detectsDuplicateFrame() {
        val width = 50
        val frameHeight = 100
        val doc = makeVirtualDocument(width, 200)
        val frame = sliceFrameWithStaticChrome(doc, width, frameHeight, 10, 12, 12)

        val match = OverlapFinder.findBestOverlap(frame, frame.copyOf(), width, frameHeight, 12, 12)
        assertTrue(match.isDuplicateFrame)
        assertTrue(match.isConfident)
    }

    @Test
    fun returnsLowConfidenceWhenUnrelated() {
        val width = 60
        val frameHeight = 120
        val doc = makeVirtualDocument(width, 400)
        val prev = sliceFrameWithStaticChrome(doc, width, frameHeight, 0, 14, 14)
        val next = sliceFrameWithStaticChrome(doc, width, frameHeight, 200, 14, 14)

        val match = OverlapFinder.findBestOverlap(prev, next, width, frameHeight, 14, 14)
        assertFalse(match.isConfident)
        assertEquals(14, match.nextNewContentStartRow)
    }

    @Test
    fun detectsSecureBlackScreen() {
        assertTrue(OverlapFinder.isLikelySecureBlackFrame(ByteArray(1000) { 0 }))
        assertFalse(OverlapFinder.isLikelySecureBlackFrame(ByteArray(1000) { (it % 255).toByte() }))
    }

    @Test
    fun boundsDimensionsPreservingAspectRatio() {
        val wScaled = OverlapFinder.computeBoundedDimensions(1440, 3200, maxWidth = 1080, maxHeight = 6000)
        assertEquals(1080, wScaled.width)
        assertEquals(2400, wScaled.height)

        val hScaled = OverlapFinder.computeBoundedDimensions(1080, 9000, maxWidth = 1080, maxHeight = 6000)
        assertEquals(720, hScaled.width)
        assertEquals(6000, hScaled.height)
    }
}
