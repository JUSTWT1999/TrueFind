package com.truefind.app.chooser

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.truefind.app.TrueFindApp
import com.truefind.app.model.CapturePayload
import com.truefind.app.ui.theme.TrueFindTheme
import java.io.File

class ChooserActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialPayload = CapturePayload.fromIntent(intent)
        if (initialPayload == null) {
            finish()
            return
        }

        val container = (application as TrueFindApp).container
        val templates = container.templateRepository.getAll()
        val lastId = container.settingsStore.lastTemplateId
        val defaultTemplate = templates.firstOrNull { it.id == lastId } ?: templates.first()
        val detectedTargets = container.aiAppRegistry.detectTargets()

        setContent {
            TrueFindTheme {
                var payload by remember { mutableStateOf(initialPayload) }
                var selectedTemplate by remember { mutableStateOf(defaultTemplate) }
                var promptDraft by remember { mutableStateOf(defaultTemplate.body) }
                var showCropDialog by remember { mutableStateOf(false) }

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = sheetState
                ) {
                    ChooserSheetContent(
                        payload = payload,
                        templates = templates,
                        selectedTemplateId = selectedTemplate.id,
                        promptDraft = promptDraft,
                        targets = detectedTargets,
                        onSelectTemplate = { tpl ->
                            selectedTemplate = tpl
                            promptDraft = tpl.body
                            container.settingsStore.lastTemplateId = tpl.id
                        },
                        onPromptDraftChange = { promptDraft = it },
                        onRequestCrop = {
                            if (payload.hasImage) showCropDialog = true
                        },
                        onDispatchTarget = { target ->
                            container.settingsStore.lastTemplateId = selectedTemplate.id
                            container.aiDispatcher.dispatchToTarget(target, payload, promptDraft)
                            finish()
                        },
                        onDispatchOther = {
                            container.settingsStore.lastTemplateId = selectedTemplate.id
                            container.aiDispatcher.dispatchWithSystemChooser(payload, promptDraft)
                            finish()
                        }
                    )
                }

                val currentImagePath = payload.imageFilePath
                if (showCropDialog && currentImagePath != null) {
                    CropOverlayDialog(
                        imagePath = currentImagePath,
                        onDismiss = { showCropDialog = false },
                        onCropConfirmed = { croppedBitmap ->
                            val newFile = try {
                                container.cacheManager.overwriteFile(File(currentImagePath), croppedBitmap)
                            } finally {
                                croppedBitmap.recycle()
                            }
                            payload = payload.copy(imageFilePath = newFile.absolutePath)
                            showCropDialog = false
                        }
                    )
                }
            }
        }
    }

    companion object {
        fun launch(context: Context, payload: CapturePayload) {
            val intent = Intent(context, ChooserActivity::class.java).apply {
                payload.writeToIntent(this)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
