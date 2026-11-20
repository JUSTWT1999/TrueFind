package com.truefind.app.stitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class OverlapFinderTest {

    private fun makeVirtualDocument(width: Int, totalRows: Int): ByteArray {
        val doc = ByteArray(width * totalRows)
        for (y in 0 until totalRows) {
            for (x in 0 until width) {
                // High-contrast synthetic text-like pattern unique per row
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
                    y < topBarRows -> 20.toByte() // Fixed status/app bar
                    y >= frameHeight - bottomBarRows -> 230.toByte() // Fixed nav bar
                    else -> {
                        val docRow = scrollOffset + (y - topBarRows)
                        doc[docRow * width + x]
                    }
                }
            }
        }
        return frame
    }

    @Test
    fun `detects exact overlap despite static top and bottom bars`() {
        val width = 60
        val frameHeight = 120
        val topBar = 16
        val bottomBar = 16
        val contentHeight = frameHeight - topBar - bottomBar // 88 rows
        val scrollDelta = 52 // 36 rows overlap (~41%)

        val doc = makeVirtualDocument(width, 300)
        val prev = sliceFrameWithStaticChrome(doc, width, frameHeight, scrollOffset = 0, topBar, bottomBar)
        val next = sliceFrameWithStaticChrome(doc, width, frameHeight, scrollOffset = scrollDelta, topBar, bottomBar)

        val match = OverlapFinder.findBestOverlap(
            prevGray = prev,
            nextGray = next,
            width = width,
            height = frameHeight,
            topIgnoreRows = topBar,
            bottomIgnoreRows = bottomBar
        )

        val expectedNewContentStart = topBar + (contentHeight - scrollDelta) // 16 + 36 = 52
        assertTrue("Expected confident overlap match, got $match", match.isConfident)
        assertFalse(match.isDuplicateFrame)
        assertEquals(expectedNewContentStart, match.nextNewContentStartRow)
        assertTrue("Expected near-zero MAD, got ${match.madScore}", match.madScore < 0.01)
    }

    @Test
    fun `detects duplicate frame when user did not scroll`() {
        val width = 50
        val frameHeight = 100
        val doc = makeVirtualDocument(width, 200)
        val frame = sliceFrameWithStaticChrome(doc, width, frameHeight, scrollOffset = 10, topBarRows = 12, bottomBarRows = 12)

        val match = OverlapFinder.findBestOverlap(
            prevGray = frame,
            nextGray = frame.copyOf(),
            width = width,
            height = frameHeight,
            topIgnoreRows = 12,
            bottomIgnoreRows = 12
        )

        assertTrue(match.isDuplicateFrame)
        assertTrue(match.isConfident)
    }

    @Test
    fun `returns low confidence fallback when frames have no overlap`() {
        val width = 60
        val frameHeight = 120
        val topBar = 14
        val bottomBar = 14
        val doc = makeVirtualDocument(width, 400)

        val prev = sliceFrameWithStaticChrome(doc, width, frameHeight, scrollOffset = 0, topBar, bottomBar)
        val next = sliceFrameWithStaticChrome(doc, width, frameHeight, scrollOffset = 200, topBar, bottomBar)

        val match = OverlapFinder.findBestOverlap(
            prevGray = prev,
            nextGray = next,
            width = width,
            height = frameHeight,
            topIgnoreRows = topBar,
            bottomIgnoreRows = bottomBar
        )

        assertFalse("Unrelated frames should not claim confident overlap", match.isConfident)
        assertEquals(topBar, match.nextNewContentStartRow)
    }

    @Test
    fun `detects FLAG_SECURE all-black screen`() {
        val allBlack = ByteArray(1000) { 0 }
        val normal = ByteArray(1000) { (it % 255).toByte() }

        assertTrue(OverlapFinder.isLikelySecureBlackFrame(allBlack))
        assertFalse(OverlapFinder.isLikelySecureBlackFrame(normal))
    }

    @Test
    fun `bounds dimensions preserving aspect ratio`() {
        val scaledWidth = OverlapFinder.computeBoundedDimensions(1440, 3200, maxWidth = 1080, maxHeight = 6000)
        assertEquals(1080, scaledWidth.width)
        assertEquals(2400, scaledWidth.height)

        val scaledHeight = OverlapFinder.computeBoundedDimensions(1080, 9000, maxWidth = 1080, maxHeight = 6000)
        assertEquals(720, scaledHeight.width)
        assertEquals(6000, scaledHeight.height)
    }
}
