package com.truefind.app.settings

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.truefind.app.R
import com.truefind.app.TrueFindApp
import com.truefind.app.capture.CaptureMode
import com.truefind.app.capture.FactCheckTileService
import com.truefind.app.capture.FloatingBubbleService
import com.truefind.app.capture.ScreenCaptureActivity
import com.truefind.app.ui.theme.TrueFindTheme
import com.truefind.shared.model.AiTargetApp
import com.truefind.shared.model.PromptTemplate

class MainActivity : ComponentActivity() {

    private var pendingEnableBubbleAfterNotif = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (pendingEnableBubbleAfterNotif) {
                pendingEnableBubbleAfterNotif = false
                tryEnableBubble()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TrueFindApp).container

        setContent {
            TrueFindTheme {
                var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(this)) }
                var hasNotifPermission by remember { mutableStateOf(hasNotificationPermission()) }
                var bubbleActive by remember {
                    mutableStateOf(FloatingBubbleService.isRunning || container.settingsStore.bubbleEnabled)
                }
                var targets by remember { mutableStateOf(container.aiAppRegistry.detectTargets()) }
                var templates by remember { mutableStateOf(container.templateRepository.getAll()) }
                var showEditor by remember { mutableStateOf(false) }
                var editingTemplate by remember { mutableStateOf<PromptTemplate?>(null) }

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasOverlayPermission = Settings.canDrawOverlays(this@MainActivity)
                            hasNotifPermission = hasNotificationPermission()
                            bubbleActive = FloatingBubbleService.isRunning
                            targets = container.aiAppRegistry.detectTargets()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                MainSettingsScreen(
                    hasOverlayPermission = hasOverlayPermission,
                    hasNotifPermission = hasNotifPermission,
                    bubbleActive = bubbleActive,
                    targets = targets,
                    templates = templates,
                    onToggleBubble = { enable ->
                        if (enable) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
                                pendingEnableBubbleAfterNotif = true
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                bubbleActive = tryEnableBubble()
                            }
                        } else {
                            container.settingsStore.bubbleEnabled = false
                            FloatingBubbleService.stop(this)
                            bubbleActive = false
                        }
                    },
                    onRequestOverlayPermission = { openOverlayPermissionSettings() },
                    onRequestAddTile = { requestQuickSettingsTile() },
                    onStartSingleCapture = {
                        moveTaskToBack(true)
                        startActivity(ScreenCaptureActivity.createIntent(this, CaptureMode.SINGLE))
                    },
                    onStartScrollCapture = {
                        if (!Settings.canDrawOverlays(this)) {
                            Toast.makeText(this, "捲動拼圖需要「顯示在其他應用程式上層」權限", Toast.LENGTH_SHORT).show()
                            openOverlayPermissionSettings()
                        } else {
                            moveTaskToBack(true)
                            startActivity(ScreenCaptureActivity.createIntent(this, CaptureMode.SCROLL_STITCH))
                        }
                    },
                    onAddTemplate = {
                        editingTemplate = null
                        showEditor = true
                    },
                    onEditTemplate = { tpl ->
                        editingTemplate = tpl
                        showEditor = true
                    },
                    onDeleteTemplate = { tpl ->
                        templates = container.templateRepository.delete(tpl.id)
                    },
                    onResetTemplates = {
                        templates = container.templateRepository.resetToDefaults()
                        Toast.makeText(this, "已還原 4 組預設查核模板", Toast.LENGTH_SHORT).show()
                    }
                )

                if (showEditor) {
                    TemplateEditorSheet(
                        editingTemplate = editingTemplate,
                        onDismiss = { showEditor = false },
                        onSave = { title, body, existingId ->
                            templates = container.templateRepository.upsert(title, body, existingId)
                            showEditor = false
                        }
                    )
                }
            }
        }
    }

    private fun tryEnableBubble(): Boolean {
        val container = (application as TrueFindApp).container
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "請先允許 TrueFind 顯示在其他應用程式上層", Toast.LENGTH_SHORT).show()
            openOverlayPermissionSettings()
            return false
        }
        container.settingsStore.bubbleEnabled = true
        FloatingBubbleService.start(this)
        return true
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sbm = getSystemService(Context.STATUS_BAR_SERVICE) as? StatusBarManager
            if (sbm != null) {
                sbm.requestAddTileService(
                    ComponentName(this, FactCheckTileService::class.java),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_truefind_tile),
                    mainExecutor
                ) { result ->
                    if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                        Toast.makeText(this, "已加入下拉狀態列快捷鍵！", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
        }
        Toast.makeText(this, "請下拉狀態列 → 點編輯圖示，手動將「TrueFind 查核」拖入快捷區", Toast.LENGTH_LONG).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MainSettingsScreen(
    hasOverlayPermission: Boolean,
    hasNotifPermission: Boolean,
    bubbleActive: Boolean,
    targets: List<AiTargetApp>,
    templates: List<PromptTemplate>,
    onToggleBubble: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestAddTile: () -> Unit,
    onStartSingleCapture: () -> Unit,
    onStartScrollCapture: () -> Unit,
    onAddTemplate: () -> Unit,
    onEditTemplate: (PromptTemplate) -> Unit,
    onDeleteTemplate: (PromptTemplate) -> Unit,
    onResetTemplates: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TrueFind", fontWeight = FontWeight.ExtraBold)
                        Text(
                            text = "一鍵派發至 ChatGPT / Gemini 事實查核",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Floating Bubble & Quick Capture
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "懸浮快捷球",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "短按：單張查核｜按壓 0.5 秒：向旁生出「截圖／捲動截圖」純圖示雙圓圈",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = bubbleActive,
                            onCheckedChange = onToggleBubble
                        )
                    }

                    if (!hasOverlayPermission) {
                        OutlinedButton(
                            onClick = onRequestOverlayPermission,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("前往開啟「顯示在其他應用程式上層」權限")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStartSingleCapture,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("選 App 單張查核")
                        }
                        OutlinedButton(
                            onClick = onStartScrollCapture,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("選 App 捲動接圖")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "下拉狀態列快捷鍵 & 系統分享",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "不開懸浮球也能用；反白選字或分享貼文連結完全免截圖彈窗",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onRequestAddTile) {
                            Text("加入磁貼")
                        }
                    }
                }
            }

            // Card 2: Detected AI Apps
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "本機已偵測 AI App",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        targets.forEach { target ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (target.isInstalled) {
                                    Color(target.accentColorHex).copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (target.isInstalled) Color(target.accentColorHex)
                                                else MaterialTheme.colorScheme.outline
                                            )
                                    )
                                    Text(
                                        text = if (target.isInstalled) "${target.displayName} ✓" else "${target.displayName}（未安裝）",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (target.isInstalled) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Section 3: Prompt Templates CRUD
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "事實查核 Prompt 模板",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        TextButton(onClick = onResetTemplates) {
                            Text("還原預設")
                        }
                        TextButton(onClick = onAddTemplate) {
                            Text("+ 新增")
                        }
                    }
                }

                templates.forEach { tpl ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tpl.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row {
                                    TextButton(onClick = { onEditTemplate(tpl) }) {
                                        Text("編輯")
                                    }
                                    if (templates.size > 1) {
                                        TextButton(onClick = { onDeleteTemplate(tpl) }) {
                                            Text("刪除", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            Text(
                                text = tpl.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Footer: Zero-network privacy badge
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Text(
                    text = "隱私保護：TrueFind 未申請任何網路（INTERNET）與相簿讀取權限；截圖僅存於 App 私有快取並在 1 小時後自動清除。" +
                        if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            "\n提示：開啟懸浮球時會向您請求通知權限以符合 Android 前景服務規範。"
                        } else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
