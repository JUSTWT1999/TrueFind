# AGENTS.md — TrueFind (Android)

TrueFind 是一款 Google Play 合規的 Android 事實查核快捷工具：透過懸浮球／狀態列快捷磁貼／系統分享擷取當下畫面（支援單張截圖與手動捲動拼圖）或文字，直接派發給手機已安裝的原生 AI App（ChatGPT、Gemini、Perplexity、Claude、Grok）。

## 核心文件索引
- 開發設計規格書：`docs/superpowers/specs/2026-11-20-truefind-android-design.md`
- 運作畫面分鏡：`docs/mockups/truefind-storyboard.html`

## Google Play 合規紅線（不可違反）
1. **禁止加入高風險權限**：`AndroidManifest.xml`（含 merged manifest）嚴禁出現：
   - `android.permission.INTERNET`（本機離線運作，零網路權限是核心信任保證）
   - `android.permission.BIND_ACCESSIBILITY_SERVICE`（不得使用無障礙服務自動捲動或模擬點擊）
   - `android.permission.QUERY_ALL_PACKAGES`（必須使用 `<queries>` 明確列舉目標 AI App）
   - `android.permission.READ_MEDIA_IMAGES` / `WRITE_EXTERNAL_STORAGE`（截圖僅存 `cacheDir/captures/`，絕不寫入公用相簿）
2. **Android 14+ MediaProjection 強制順序**：必須先呼叫 `startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`，才能呼叫 `MediaProjectionManager.getMediaProjection()`；並註冊 `MediaProjection.Callback.onStop()`。
3. **不常駐螢幕投影**：單張截圖完成立刻停止 `VirtualDisplay` 與前景服務；捲動拼圖上限 5 頁且閒置 60 秒自動釋放。

## 架構與實作守則
- **極簡優先**：Kotlin + Jetpack Compose (Material 3)，`minSdk = 29`、`targetSdk = 35`。不引入 Room、Hilt、OpenCV 等重型框架；偏好與模板用 `SharedPreferences` + `org.json`。
- **純邏輯與 Android API 解耦**：`stitch/OverlapFinder.kt`、`dispatch/PromptComposer.kt`、`data/PromptTemplateRepository.kt` 的核心邏輯必須可透過純 JVM 單元測試驗證，避免引入 Robolectric。
- **派發剪貼簿防呆**：`AiDispatcher` 啟動目標 AI App 前，一律將最終 Prompt 寫入剪貼簿並顯示短提示，防範部分 AI App 接收圖片時忽略 `EXTRA_TEXT`。
- **截圖防自拍**：觸發截圖前先隱藏懸浮球／拼圖浮動條至少 120–150ms，確保不截進自身 UI；並偵測 `FLAG_SECURE` 全黑畫面給予友善提示。
- **記憶體與快取清理**：長圖拼接寬度上限 `1080px`、總高度上限 `6000px`，中間 `Bitmap` 用完立即 `recycle()`；啟動與派發後自動清理超過 1 小時的暫存截圖。

## 常用建置與驗證指令
```powershell
# 執行純 JVM 單元測試
.\gradlew.bat testDebugUnitTest

# 編譯 Debug APK
.\gradlew.bat assembleDebug

# 稽核 merged manifest，確認無禁用權限
Select-String -Path "app\build\intermediates\merged_manifest\debug\processDebugMainManifest\AndroidManifest.xml" -Pattern "INTERNET|ACCESSIBILITY|QUERY_ALL_PACKAGES|READ_MEDIA_IMAGES"
```
