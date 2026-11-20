# AGENTS.md — TrueFind (Android & iOS KMP Monorepo)

TrueFind 是一款雙平台（Android + iOS）商店合規的事實查核快捷工具：擷取當下畫面或文字，一鍵套用查核 Prompt 模板並派發給手機已安裝的原生 AI App（ChatGPT、Gemini、Perplexity、Claude、Grok）。

## 專案結構（KMP Monorepo）
- `shared/`：Kotlin Multiplatform 共享核心（Prompt 模板與 JSON 存取、Prompt 組合器 `PromptComposer`、捲動長圖重疊演算法 `OverlapFinder`、支援 AI App 清單 `KnownAiTargets`）。編譯目標為 `jvm()`（供 Android）與 `XCFramework("TrueFindShared")`（供 iOS）。
- `androidApp/`：Android 原生 App（Kotlin + Jetpack Compose Material 3，`minSdk = 29`、`targetSdk = 35`），實作懸浮球、狀態列快捷磁貼、`MediaProjection` 單張與手動捲動拼圖截圖、系統分享／反白選字與 `FileProvider` 派發。
- `iosApp/`：iOS 原生 App（SwiftUI + `XcodeGen` `project.yml`，iOS 16+），透過 `TrueFindShared.xcframework` 共享核心邏輯，實作 iOS Share Extension（系統分享）與 App Intents（背面輕點／動作按鈕截圖捷徑）。
- `.github/workflows/build.yml`：雙平台 CI（Ubuntu 編譯驗證 Android APK；macOS 編譯 KMP XCFramework + iOS App）。

## 核心文件索引
- 開發設計規格書：`docs/superpowers/specs/2026-11-20-truefind-android-design.md`
- 運作畫面分鏡：`docs/mockups/truefind-storyboard.html`

## Google Play / App Store 合規紅線（不可違反）
1. **禁止加入高風險權限**：`androidApp` 的 `AndroidManifest.xml`（含 merged manifest）嚴禁出現：
   - `android.permission.INTERNET`（零網路權限是核心信任保證）
   - `android.permission.BIND_ACCESSIBILITY_SERVICE`（不得使用無障礙服務自動捲動或模擬點擊）
   - `android.permission.QUERY_ALL_PACKAGES`（必須使用 `<queries>` 明確列舉目標 AI App）
   - `android.permission.READ_MEDIA_IMAGES` / `WRITE_EXTERNAL_STORAGE`（截圖僅存私有快取，不寫入相簿）
2. **Android 14+ MediaProjection 強制順序**：必須先呼叫 `startForeground(..., FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)`，才能呼叫 `MediaProjectionManager.getMediaProjection()`；並註冊 `MediaProjection.Callback.onStop()`。
3. **不常駐螢幕投影**：單張截圖完成立刻停止 `VirtualDisplay` 與前景服務；捲動拼圖上限 5 頁且閒置 60 秒自動釋放。
4. **iOS Share Extension 安全限制**：`ChooserSheetView.swift` 會同時編譯進主 App 與 Share Extension（`APPLICATION_EXTENSION_API_ONLY = YES`），禁止在其中直接存取 `UIApplication.shared`。

## 架構與實作守則
- **極簡優先**：不引入 Room、Hilt、OpenCV 等重型框架；共享邏輯集中於 `shared/src/commonMain` 並以 `kotlin.test` 驗證。
- **派發剪貼簿防呆**：雙平台派發前一律將最終 Prompt 寫入剪貼簿並給提示，防範部分 AI App 接收圖片時忽略文字。
- **截圖防自拍與快取清理**：截圖前隱藏懸浮球／拼圖浮動條至少 120–150ms；偵測 `FLAG_SECURE` 全黑畫面；長圖上限 `1080px × 6000px`；暫存截圖超過 1 小時自動清除。

## 常用建置與驗證指令
```powershell
# 執行 KMP shared + Android 全部單元測試，並編譯 Android Debug APK
.\gradlew.bat :shared:jvmTest :androidApp:testDebugUnitTest :androidApp:assembleDebug

# 稽核 Android merged manifest，確認無禁用權限
Select-String -Path "androidApp\build\intermediates\merged_manifest\debug\processDebugMainManifest\AndroidManifest.xml" -Pattern "INTERNET|ACCESSIBILITY|QUERY_ALL_PACKAGES|READ_MEDIA_IMAGES"
```
```bash
# macOS / CI 編譯 iOS 共享 Framework 與 Xcode 專案
./gradlew :shared:iosSimulatorArm64Test :shared:assembleTrueFindSharedReleaseXCFramework
cd iosApp && xcodegen generate
```
