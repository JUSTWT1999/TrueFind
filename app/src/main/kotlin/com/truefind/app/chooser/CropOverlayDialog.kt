package com.truefind.app.chooser

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class DragHandle { TOP, BOTTOM, LEFT, RIGHT, MOVE }

@Composable
fun CropOverlayDialog(
    imagePath: String,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    val sourceBitmap = remember(imagePath) { BitmapFactory.decodeFile(imagePath) }
    if (sourceBitmap == null) {
        onDismiss()
        return
    }

    var leftFrac by remember { mutableFloatStateOf(0.04f) }
    var topFrac by remember { mutableFloatStateOf(0.10f) }
    var rightFrac by remember { mutableFloatStateOf(0.96f) }
    var bottomFrac by remember { mutableFloatStateOf(0.88f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableStateOf<DragHandle?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0D12))
                .systemBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = Color(0xFFCBD5E1))
                }
                Text(
                    text = "拖曳藍框裁切重點區域",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Button(
                    onClick = {
                        val x = (leftFrac * sourceBitmap.width).roundToInt().coerceIn(0, sourceBitmap.width - 1)
                        val y = (topFrac * sourceBitmap.height).roundToInt().coerceIn(0, sourceBitmap.height - 1)
                        val w = ((rightFrac - leftFrac) * sourceBitmap.width).roundToInt()
                            .coerceIn(1, sourceBitmap.width - x)
                        val h = ((bottomFrac - topFrac) * sourceBitmap.height).roundToInt()
                            .coerceIn(1, sourceBitmap.height - y)
                        val cropped = Bitmap.createBitmap(sourceBitmap, x, y, w, h)
                        onCropConfirmed(cropped)
                    }
                ) {
                    Text("完成")
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val imageBitmap = remember(sourceBitmap) { sourceBitmap.asImageBitmap() }
                Box(
                    modifier = Modifier
                        .onSizeChanged { boxSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val w = boxSize.width.toFloat().coerceAtLeast(1f)
                                    val h = boxSize.height.toFloat().coerceAtLeast(1f)
                                    val fx = offset.x / w
                                    val fy = offset.y / h
                                    val edgeSlop = 0.08f
                                    activeHandle = when {
                                        abs(fy - topFrac) < edgeSlop -> DragHandle.TOP
                                        abs(fy - bottomFrac) < edgeSlop -> DragHandle.BOTTOM
                                        abs(fx - leftFrac) < edgeSlop -> DragHandle.LEFT
                                        abs(fx - rightFrac) < edgeSlop -> DragHandle.RIGHT
                                        else -> DragHandle.MOVE
                                    }
                                },
                                onDragEnd = { activeHandle = null },
                                onDragCancel = { activeHandle = null },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val w = boxSize.width.toFloat().coerceAtLeast(1f)
                                    val h = boxSize.height.toFloat().coerceAtLeast(1f)
                                    val dx = dragAmount.x / w
                                    val dy = dragAmount.y / h
                                    val minSpan = 0.12f

                                    when (activeHandle) {
                                        DragHandle.TOP -> {
                                            topFrac = (topFrac + dy).coerceIn(0f, bottomFrac - minSpan)
                                        }
                                        DragHandle.BOTTOM -> {
                                            bottomFrac = (bottomFrac + dy).coerceIn(topFrac + minSpan, 1f)
                                        }
                                        DragHandle.LEFT -> {
                                            leftFrac = (leftFrac + dx).coerceIn(0f, rightFrac - minSpan)
                                        }
                                        DragHandle.RIGHT -> {
                                            rightFrac = (rightFrac + dx).coerceIn(leftFrac + minSpan, 1f)
                                        }
                                        DragHandle.MOVE -> {
                                            val spanX = rightFrac - leftFrac
                                            val spanY = bottomFrac - topFrac
                                            val newLeft = (leftFrac + dx).coerceIn(0f, 1f - spanX)
                                            val newTop = (topFrac + dy).coerceIn(0f, 1f - spanY)
                                            leftFrac = newLeft
                                            rightFrac = newLeft + spanX
                                            topFrac = newTop
                                            bottomFrac = newTop + spanY
                                        }
                                        null -> Unit
                                    }
                                }
                            )
                        }
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "待裁切截圖",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )

                    Canvas(modifier = Modifier.matchParentSize()) {
                        val l = size.width * leftFrac
                        val t = size.height * topFrac
                        val r = size.width * rightFrac
                        val b = size.height * bottomFrac
                        val dim = Color(0x99000000)

                        drawRect(dim, Offset.Zero, Size(size.width, t))
                        drawRect(dim, Offset(0f, b), Size(size.width, max(0f, size.height - b)))
                        drawRect(dim, Offset(0f, t), Size(l, max(0f, b - t)))
                        drawRect(dim, Offset(r, t), Size(max(0f, size.width - r), max(0f, b - t)))

                        drawRect(
                            color = Color(0xFF3B82F6),
                            topLeft = Offset(l, t),
                            size = Size(min(size.width, r - l), min(size.height, b - t)),
                            style = Stroke(width = 3.dp.toPx())
                        )

                        val handleRadius = 7.dp.toPx()
                        listOf(
                            Offset((l + r) / 2f, t),
                            Offset((l + r) / 2f, b),
                            Offset(l, (t + b) / 2f),
                            Offset(r, (t + b) / 2f)
                        ).forEach { center ->
                            drawCircle(Color(0xFF60A5FA), radius = handleRadius, center = center)
                        }
                    }
                }
            }
        }
    }
}
