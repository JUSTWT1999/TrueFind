# TrueFind iPhone（iOS）安裝與使用說明

本文件說明如何在 **沒有 Mac 電腦** 的情況下，直接從 Windows 電腦將 GitHub Actions 編譯好的 TrueFind iOS 版安裝到你的實體 iPhone，並設定「背面輕點兩下」一鍵查核。

- **系統需求**：iPhone（iOS 16.0 以上）、Windows 電腦、USB 傳輸線、個人 Apple ID
- **操作畫面分鏡參考**：[truefind-ios-sample.html](mockups/truefind-ios-sample.html)

---

## 一、事前準備（Windows 電腦）

1. **安裝 Apple 官方傳統版 iTunes 與 iCloud**（Sideloadly 必要元件，**請勿從 Microsoft Store 安裝**）：
   - iTunes（Windows 64-bit 傳統安裝檔）：https://www.apple.com/itunes/download/win64
   - iCloud for Windows（傳統安裝檔，Sideloadly 官網亦提供連結）：https://sideloadly.io/
2. **下載並安裝 Sideloadly**（免費 iOS 側載簽章工具）：
   - 官網下載：https://sideloadly.io/

> **小提示**：如果你想每 7 天透過 Wi-Fi 全自動背景續簽，也可以改用 [AltStore](https://altstore.io/)；若希望操作步驟最少，推薦先用 **Sideloadly**。

---

## 二、下載最新版 iPhone `.ipa` 安裝檔

1. 打開專案的 GitHub Actions 頁面：
   https://github.com/JUSTWT1999/TrueFind/actions
2. 點進最新一筆**綠色打勾**的 `TrueFind CI (Android & iOS)` 建置紀錄（例如 Run #6）。
3. 滑到該頁面最底部的 **Artifacts** 區塊，點擊下載 **`TrueFind-iOS-Unsigned-IPA`**。
4. 將下載下來的 `.zip` 壓縮檔**解壓縮**，取出裡面的 **`TrueFind-iOS-unsigned.ipa`**。

> 注意：一定要先解壓出 `.ipa` 檔案，不能把 `.zip` 直接拖進 Sideloadly。

---

## 三、用 Sideloadly 安裝到 iPhone

1. 用 USB 傳輸線將 iPhone 連接到 Windows 電腦。
   - 若 iPhone 螢幕跳出「**要信任這部電腦嗎？**」，請點「**信任**」並輸入 iPhone 解鎖密碼。
2. 打開電腦上的 **Sideloadly**：
   - **iDevice**：確認已顯示你的 iPhone 型號。
   - **IPA**：將解壓出來的 `TrueFind-iOS-unsigned.ipa` 拖曳到左側 `IPA` 圖示框中。
   - **Apple ID**：輸入你的 Apple ID Email（若不想用主帳號，可另外免費註冊一組備用 Apple ID 專門側載）。
   - （選用）展開 **Advanced Options** 可勾選 Wi-Fi 自動續簽（Auto Refresh）。
3. 點擊 **Start**，依提示輸入 Apple ID 密碼與 6 位數雙重驗證碼。
4. 等待進度條跑完並顯示 **`Done.`**，iPhone 桌面就會出現 **TrueFind** 圖示！

---

## 四、首次開啟前必做的 2 項 iPhone 系統設定

剛側載完成直接點開 App 會被 iOS 擋下，請先完成以下兩步（只需做一次）：

1. **信任開發者憑證**：
   - 打開 iPhone **「設定 → 一般 → VPN 與裝置管理」**
   - 在「開發者 App」下方點擊你的 Apple ID
   - 點擊 **「信任『你的 Apple ID』」** → 再次確認「信任」
2. **開啟開發者模式（iOS 16 以上必做）**：
   - 打開 iPhone **「設定 → 隱私權與安全性」**
   - 滑到最下方點 **「開發者模式」** → 開啟開關並依提示**重新開機**
   - 重開機後輸入密碼確認「開啟開發者模式」

完成後即可正常打開 **TrueFind**！

---

## 五、設定「背面輕點兩下」0 彈窗一鍵截圖查核（推薦，約 30 秒）

「背面輕點（Back Tap）」是 iPhone 8 以上內建的官方手勢：看貼文時用食指在 **iPhone 機背蘋果 Logo 附近輕敲兩下（叩、叩）**，系統會靜默自動截圖並直接跳出 TrueFind 查核面板。

### 1. 建立 2 行 Apple 捷徑
1. 打開 iPhone 內建的 **「捷徑（Shortcuts）」App**。
2. 點右上角 **`＋`** 新增一個捷徑。
3. 點「加入動作」→ 搜尋並加入 **「拍攝截圖」**。
4. 再點下方搜尋列 → 搜尋 **`TrueFind`** → 加入 **「事實查核截圖」**（系統會自動把上一步的「螢幕截圖」帶入參數）。
5. 點頂部名稱改名為 **「TrueFind 查核」** → 點「完成」。

### 2. 綁定到「背面輕點」或「動作按鈕」
- **一般 iPhone（iPhone 8 以上）**：
  前往 **「設定 → 輔助使用 → 觸控 → 背面輕點 → 點兩下」** → 滑到最下方「捷徑」區選擇 **「TrueFind 查核」**。
- **iPhone 15 Pro / 16 以上機型**：
  也可以前往 **「設定 → 動作按鈕 → 捷徑」** 選擇 **「TrueFind 查核」**，按一下側邊實體按鈕就直接截圖查核！

> **第一次觸發提示**：首次敲背面兩下時，iOS 會跳一次隱私確認「是否允許捷徑將截圖分享給 TrueFind？」→ 請點 **「永遠允許」**，之後就完全 0 彈窗直達面板。

---

## 六、其他兩種使用方式（系統分享＆長文多圖拼接）

1. **系統分享延伸功能（Share Extension）**：
   - 看 Threads / Facebook / Safari / 新聞網頁時，按系統「分享 → **TrueFind 查核**」（Safari 網頁更可先拍「整頁截圖」再直接分享）。
   - **長文多頁拼接**：在 iPhone 相簿一次勾選 **2～5 張連續截圖** → 點「分享 → **TrueFind 查核**」，TrueFind 會自動用 KMP 演算法去除重疊處，無縫拼成一張長圖送給 ChatGPT / Gemini！
2. **App 內免權限選圖拼接**：
   - 打開 TrueFind 主頁，點擊 **「從相簿挑選 1～5 張截圖拼接查核（免相簿權限）」**，一樣可以裁切並一鍵派發。

---

## 七、常見問題（FAQ）

### Q1：免費 Apple ID 側載 7 天到期怎麼辦？
- TrueFind 本身沒有任何期限限制，7 天是 Apple 對免費側載簽章的規定。
- **續簽方式**：只要用 Sideloadly 再把同一個 `.ipa` 覆蓋安裝一次（或啟用 Sideloadly / AltStore 的 Wi-Fi 自動續簽），原本設定好的查核模板與捷徑**全部都會保留**。
- 若未來使用付費 Apple Developer 帳號（US\$99／年）透過 **TestFlight** 發佈，有效期為 90 天；正式上架 **App Store** 則為永久無期限。

### Q2：安裝時出現 `Maximum number of apps / App IDs` 錯誤？
- Apple 免費個人帳號規定同一支 iPhone 最多同時側載 3 個 App，且 7 天內最多註冊 10 個 App ID。
- TrueFind 因為包含「主 App ＋ Share Extension」，會使用 2 個 App ID。若你手機上已側載其他 App 導致額度不足，可先刪除舊的側載 App，或在 Sideloadly 的 `Advanced Options` 勾選 `Remove PlugIns`（僅裝主 App，只佔 1 個 App ID，但會停用系統分享延伸功能，背面輕點與相簿選圖仍可正常使用）。

### Q3：送到 Gemini 時只有照片進去、文字沒有自動填入？
- 這是 Gemini App 的接收行為限制。TrueFind 在派發前已自動把最終查核指令複製到 iPhone 剪貼簿，進入 Gemini 後點一下輸入框選「**貼上**」即可立即送出。
