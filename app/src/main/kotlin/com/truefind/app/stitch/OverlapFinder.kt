package com.truefind.app.stitch

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class OverlapMatch(
    /** Row index in the next frame (downscaled coordinates) where new non-overlapping content begins. */
    val nextNewContentStartRow: Int,
    /** Normalized Mean Absolute Difference in [0.0, 1.0]. Lower is better. */
    val madScore: Double,
    /** Whether the match passed texture and error thresholds for seamless overlap stitching. */
    val isConfident: Boolean,
    /** True when the two frames appear nearly identical (user did not scroll). */
    val isDuplicateFrame: Boolean = false
)

data class ScaledDimensions(val width: Int, val height: Int)

/**
 * Pure-Kotlin vertical overlap finder operating on downscaled 8-bit grayscale byte buffers.
 * Zero Android framework dependencies so all stitching math is directly JVM unit-testable.
 */
object OverlapFinder {

    const val DEFAULT_MAX_MAD = 0.08
    private const val MIN_TEMPLATE_SPREAD = 8.0

    /**
     * Finds where the bottom region of [prevGray] reappears inside [nextGray] after scrolling down.
     *
     * [topIgnoreRows] and [bottomIgnoreRows] mask static UI chrome (status bar, app bar, nav bar).
     */
    fun findBestOverlap(
        prevGray: ByteArray,
        nextGray: ByteArray,
        width: Int,
        height: Int,
        topIgnoreRows: Int,
        bottomIgnoreRows: Int,
        maxMadThreshold: Double = DEFAULT_MAX_MAD
    ): OverlapMatch {
        require(width > 0 && height > 0) { "Invalid image dimensions: ${width}x$height" }
        require(prevGray.size >= width * height && nextGray.size >= width * height) {
            "Grayscale buffer smaller than width * height"
        }

        val contentTop = topIgnoreRows.coerceIn(0, height - 1)
        val contentBottom = (height - bottomIgnoreRows).coerceIn(contentTop + 1, height)
        val contentHeight = contentBottom - contentTop

        if (contentHeight < 16) {
            return OverlapMatch(
                nextNewContentStartRow = contentTop,
                madScore = 1.0,
                isConfident = false
            )
        }

        // Quick duplicate check: if the entire content band barely changed, user didn't scroll.
        val wholeBandMad = computeRegionMad(
            prevGray, contentTop,
            nextGray, contentTop,
            width, contentHeight
        )
        if (wholeBandMad < 0.015) {
            return OverlapMatch(
                nextNewContentStartRow = contentBottom,
                madScore = wholeBandMad,
                isConfident = true,
                isDuplicateFrame = true
            )
        }

        val templateH = (contentHeight * 0.20).roundToInt().coerceIn(8, contentHeight / 2)
        // Pick the lowest textured anchor strip in prev's content band (avoids matching blank margins).
        val templateStartInPrev = pickTexturedAnchorRow(
            gray = prevGray,
            width = width,
            contentTop = contentTop,
            contentBottom = contentBottom,
            templateH = templateH
        ) ?: return OverlapMatch(
            nextNewContentStartRow = contentTop,
            madScore = 1.0,
            isConfident = false
        )

        val anchorDistanceToBottom = contentBottom - (templateStartInPrev + templateH)
        val minScrollRows = max(2, (contentHeight * 0.04).roundToInt())
        val maxCandidateY = (templateStartInPrev - minScrollRows).coerceAtLeast(contentTop)
        val minCandidateY = contentTop

        var bestY = contentTop
        var bestMad = Double.MAX_VALUE

        // ponytail: O(H * templateH * W) scan on 180px-wide downscaled buffer (~15-30ms on mobile);
        // upgrade to 1D row-projection prefilter if strip width or frame height grows 4x+.
        for (y in minCandidateY..maxCandidateY) {
            val mad = computeRegionMad(
                prevGray, templateStartInPrev,
                nextGray, y,
                width, templateH
            )
            if (mad < bestMad) {
                bestMad = mad
                bestY = y
            }
        }

        val newContentStart = (bestY + templateH + anchorDistanceToBottom)
            .coerceIn(contentTop, contentBottom)

        val isConfident = bestMad < maxMadThreshold && newContentStart > contentTop
        return OverlapMatch(
            nextNewContentStartRow = if (isConfident) newContentStart else contentTop,
            madScore = bestMad,
            isConfident = isConfident
        )
    }

    /**
     * Returns true when more than [blackRatioThreshold] of sampled luminance pixels are near-black,
     * indicating a FLAG_SECURE / DRM protected screen capture.
     */
    fun isLikelySecureBlackFrame(
        gray: ByteArray,
        darkPixelThreshold: Int = 8,
        blackRatioThreshold: Double = 0.98
    ): Boolean {
        if (gray.isEmpty()) return true
        var darkCount = 0
        for (b in gray) {
            if ((b.toInt() and 0xFF) <= darkPixelThreshold) darkCount++
        }
        return darkCount.toDouble() / gray.size >= blackRatioThreshold
    }

    /**
     * Computes output dimensions bounded by [maxWidth] and [maxHeight] while preserving aspect ratio.
     */
    fun computeBoundedDimensions(
        width: Int,
        height: Int,
        maxWidth: Int = 1080,
        maxHeight: Int = 6000
    ): ScaledDimensions {
        require(width > 0 && height > 0) { "Dimensions must be positive" }
        val widthScale = if (width > maxWidth) maxWidth.toDouble() / width else 1.0
        val heightScale = if (height > maxHeight) maxHeight.toDouble() / height else 1.0
        val scale = min(widthScale, heightScale)
        return ScaledDimensions(
            width = max(1, (width * scale).roundToInt()),
            height = max(1, (height * scale).roundToInt())
        )
    }

    private fun pickTexturedAnchorRow(
        gray: ByteArray,
        width: Int,
        contentTop: Int,
        contentBottom: Int,
        templateH: Int
    ): Int? {
        val lowestStart = contentBottom - templateH
        val highestStart = max(contentTop + (contentBottom - contentTop) / 3, contentTop)
        val step = max(1, templateH / 2)

        var candidate = lowestStart
        while (candidate >= highestStart) {
            if (computeSpread(gray, candidate, width, templateH) >= MIN_TEMPLATE_SPREAD) {
                return candidate
            }
            candidate -= step
        }
        return null
    }

    private fun computeSpread(gray: ByteArray, startRow: Int, width: Int, rows: Int): Double {
        var sum = 0L
        val count = width * rows
        val startIdx = startRow * width
        for (i in 0 until count) {
            sum += gray[startIdx + i].toInt() and 0xFF
        }
        val mean = sum.toDouble() / count
        var devSum = 0.0
        for (i in 0 until count) {
            devSum += abs((gray[startIdx + i].toInt() and 0xFF) - mean)
        }
        return devSum / count
    }

    private fun computeRegionMad(
        bufA: ByteArray,
        startRowA: Int,
        bufB: ByteArray,
        startRowB: Int,
        width: Int,
        rows: Int
    ): Double {
        var diffSum = 0L
        val count = width * rows
        val offsetA = startRowA * width
        val offsetB = startRowB * width
        for (i in 0 until count) {
            val a = bufA[offsetA + i].toInt() and 0xFF
            val b = bufB[offsetB + i].toInt() and 0xFF
            diffSum += abs(a - b)
        }
        return diffSum.toDouble() / (count * 255.0)
    }
}
