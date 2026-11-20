package com.truefind.app.chooser

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truefind.app.model.CapturePayload
import com.truefind.shared.model.AiTargetApp
import com.truefind.shared.model.CaptureSource
import com.truefind.shared.model.PromptTemplate

@Composable
fun ChooserSheetContent(
    payload: CapturePayload,
    templates: List<PromptTemplate>,
    selectedTemplateId: String,
    promptDraft: String,
    targets: List<AiTargetApp>,
    onSelectTemplate: (PromptTemplate) -> Unit,
    onPromptDraftChange: (String) -> Unit,
    onRequestCrop: () -> Unit,
    onDispatchTarget: (AiTargetApp) -> Unit,
    onDispatchOther: () -> Unit
) {
    var promptExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TrueFind 事實查核",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (payload.frameCount > 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "已拼接 ${payload.frameCount} 頁",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Capture preview with inline optional crop action
        CapturePreviewCard(payload = payload, onRequestCrop = onRequestCrop)

        // Prompt template chips
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "1. 查核類型",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                templates.forEach { template ->
                    FilterChip(
                        selected = template.id == selectedTemplateId,
                        onClick = { onSelectTemplate(template) },
                        label = { Text(template.title) }
                    )
                }
            }
        }

        // Expandable prompt quick-edit area
        if (promptExpanded) {
            OutlinedTextField(
                value = promptDraft,
                onValueChange = onPromptDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("本次查核指令（可臨時微調）") },
                minLines = 3,
                maxLines = 5
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { promptExpanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = promptDraft,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "點此展開微調指令",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // One-tap AI App launcher row
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "2. 點選 AI 立即送出",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val installedTargets = remember(targets) { targets.filter { it.isInstalled } }
            if (installedTargets.isEmpty()) {
                Text(
                    text = "尚未偵測到 ChatGPT / Gemini，請使用下方「其他 App」透過系統分享送出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                installedTargets.forEach { target ->
                    val compatible = target.supports(payload.hasImage)
                    AiAppIconButton(
                        badgeText = target.badgeText,
                        label = target.displayName,
                        subtitle = if (compatible) null else "僅支援文字",
                        accentColor = Color(target.accentColorHex),
                        enabled = compatible,
                        onClick = { onDispatchTarget(target) }
                    )
                }

                AiAppIconButton(
                    badgeText = "⋯",
                    label = "其他 App",
                    subtitle = null,
                    accentColor = Color(0xFF475569),
                    enabled = true,
                    onClick = onDispatchOther
                )
            }
        }
    }
}

@Composable
private fun CapturePreviewCard(
    payload: CapturePayload,
    onRequestCrop: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        val imagePath = payload.imageFilePath
        if (imagePath != null) {
            val previewBitmap = remember(imagePath) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(imagePath, opts)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = "已擷取畫面縮圖",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 60.dp, height = 78.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(10.dp)
                            )
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val title = when (payload.source) {
                        CaptureSource.SCROLL_STITCH -> "已拼接長截圖（${payload.frameCount} 頁）"
                        CaptureSource.DIGITAL_ASSISTANT -> "已零彈窗快照目前畫面"
                        CaptureSource.SHARE_IMAGE -> "已接收分享圖片"
                        else -> "已擷取目前畫面"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "可先裁切重點，或直接點下方 AI 送出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onRequestCrop) {
                    Text("✂ 裁切", fontSize = 12.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (payload.source == CaptureSource.PROCESS_TEXT) "已反白選取文字" else "已接收分享內容",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = payload.text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AiAppIconButton(
    badgeText: String,
    label: String,
    subtitle: String?,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(68.dp)
            .alpha(if (enabled) 1f else 0.42f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(accentColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontSize = 10.sp
            )
        }
    }
}
