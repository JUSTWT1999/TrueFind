package com.truefind.app.stitch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.roundToInt

object VerticalBitmapStitcher {

    private const val ANALYSIS_WIDTH = 180
    private const val MAX_OUTPUT_WIDTH = 1080
    private const val MAX_OUTPUT_HEIGHT = 6000
    private const val DIVIDER_HEIGHT_PX = 4

    fun isSecureBlackScreen(bitmap: Bitmap): Boolean {
        val sample = Bitmap.createScaledBitmap(bitmap, 64, 64, false)
        return try {
            val gray = toGrayscaleBytes(sample)
            OverlapFinder.isLikelySecureBlackFrame(gray)
        } finally {
            if (sample !== bitmap) sample.recycle()
        }
    }

    /**
     * Stitches 1..N captured frames vertically into a single bounded Bitmap.
     * Caller owns [frames]; this method does not recycle caller-owned input bitmaps.
     */
    fun stitch(
        frames: List<Bitmap>,
        statusBarHeightPx: Int,
        navBarHeightPx: Int
    ): Bitmap {
        require(frames.isNotEmpty()) { "At least one frame is required" }

        val normalized = frames.map { normalizeWidth(it) }
        try {
            if (normalized.size == 1) {
                return normalized.first().copy(Bitmap.Config.ARGB_8888, false)
            }

            val baseW = normalized.first().width
            val baseH = normalized.first().height
            val topIgnoreFull = max(statusBarHeightPx, (baseH * 0.14f).roundToInt())
                .coerceIn(0, baseH / 3)
            val bottomIgnoreFull = max(navBarHeightPx, (baseH * 0.14f).roundToInt())
                .coerceIn(0, baseH / 3)

            val analysisH = max(32, (baseH.toFloat() * ANALYSIS_WIDTH / baseW).roundToInt())
            val scaleYToFull = baseH.toDouble() / analysisH.toDouble()
            val topIgnoreSmall = (topIgnoreFull / scaleYToFull).roundToInt()
            val bottomIgnoreSmall = (bottomIgnoreFull / scaleYToFull).roundToInt()

            val grayFrames = normalized.map { frame ->
                val small = Bitmap.createScaledBitmap(frame, ANALYSIS_WIDTH, analysisH, true)
                try {
                    toGrayscaleBytes(small)
                } finally {
                    if (small !== frame) small.recycle()
                }
            }

            data class SliceSpec(
                val frame: Bitmap,
                val srcTop: Int,
                val srcBottom: Int,
                val drawDividerAbove: Boolean
            )

            val slices = mutableListOf<SliceSpec>()
            // First frame keeps top chrome down to content bottom (trimmed below nav bar)
            var lastKeptIndex = 0
            slices.add(
                SliceSpec(
                    frame = normalized[0],
                    srcTop = 0,
                    srcBottom = baseH - bottomIgnoreFull,
                    drawDividerAbove = false
                )
            )

            for (i in 1 until normalized.size) {
                val match = OverlapFinder.findBestOverlap(
                    prevGray = grayFrames[lastKeptIndex],
                    nextGray = grayFrames[i],
                    width = ANALYSIS_WIDTH,
                    height = analysisH,
                    topIgnoreRows = topIgnoreSmall,
                    bottomIgnoreRows = bottomIgnoreSmall
                )

                if (match.isDuplicateFrame) continue

                val isLast = i == normalized.lastIndex
                val srcTop = if (match.isConfident) {
                    (match.nextNewContentStartRow * scaleYToFull).roundToInt()
                        .coerceIn(topIgnoreFull, baseH - bottomIgnoreFull)
                } else {
                    topIgnoreFull
                }
                val srcBottom = if (isLast) baseH else baseH - bottomIgnoreFull
                if (srcBottom - srcTop > 8) {
                    slices.add(
                        SliceSpec(
                            frame = normalized[i],
                            srcTop = srcTop,
                            srcBottom = srcBottom,
                            drawDividerAbove = !match.isConfident
                        )
                    )
                    lastKeptIndex = i
                }
            }

            // If all later frames were duplicates, restore full first frame
            if (slices.size == 1) {
                return normalized.first().copy(Bitmap.Config.ARGB_8888, false)
            }

            val totalHeight = slices.sumOf { (it.srcBottom - it.srcTop) + if (it.drawDividerAbove) DIVIDER_HEIGHT_PX else 0 }
            val rawCombined = Bitmap.createBitmap(baseW, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(rawCombined)
            val dividerPaint = Paint().apply { color = Color.parseColor("#CBD5E1") }

            var dstY = 0
            for (slice in slices) {
                if (slice.drawDividerAbove) {
                    canvas.drawRect(0f, dstY.toFloat(), baseW.toFloat(), (dstY + DIVIDER_HEIGHT_PX).toFloat(), dividerPaint)
                    dstY += DIVIDER_HEIGHT_PX
                }
                val h = slice.srcBottom - slice.srcTop
                val srcRect = Rect(0, slice.srcTop, baseW, slice.srcBottom)
                val dstRect = Rect(0, dstY, baseW, dstY + h)
                canvas.drawBitmap(slice.frame, srcRect, dstRect, null)
                dstY += h
            }

            val bounded = OverlapFinder.computeBoundedDimensions(
                width = rawCombined.width,
                height = rawCombined.height,
                maxWidth = MAX_OUTPUT_WIDTH,
                maxHeight = MAX_OUTPUT_HEIGHT
            )
            if (bounded.width == rawCombined.width && bounded.height == rawCombined.height) {
                return rawCombined
            }
            val scaled = Bitmap.createScaledBitmap(rawCombined, bounded.width, bounded.height, true)
            rawCombined.recycle()
            return scaled
        } finally {
            normalized.forEachIndexed { index, bmp ->
                if (bmp !== frames[index]) bmp.recycle()
            }
        }
    }

    private fun normalizeWidth(src: Bitmap): Bitmap {
        if (src.width <= MAX_OUTPUT_WIDTH) return src
        val targetH = max(1, (src.height.toDouble() * MAX_OUTPUT_WIDTH / src.width).roundToInt())
        return Bitmap.createScaledBitmap(src, MAX_OUTPUT_WIDTH, targetH, true)
    }

    private fun toGrayscaleBytes(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) ushr 8
            out[i] = lum.toByte()
        }
        return out
    }
}
