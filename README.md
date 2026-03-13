# Project Cyber-Navi ⚡
## 靈魂寄生型 AI 領航員 — Phase 1 Build

---

## 📂 項目結構

```
CyberNavi/
├── app/src/main/
│   ├── java/com/cybernavi/thunder/
│   │   ├── MainActivity.kt              ← 設定頁面（入口）
│   │   ├── service/
│   │   │   ├── FloatingWindowService.kt ← 懸浮視窗服務（核心）
│   │   │   └── ThunderAccessibilityService.kt ← 截獲系統AI輸出
│   │   ├── ui/
│   │   │   └── ThunderFloatingView.kt  ← 雷霆角色UI + 對話氣泡
│   │   ├── persona/
│   │   │   └── ThunderPersona.kt       ← 廣東話轉譯引擎
│   │   └── util/
│   │       ├── PermissionHelper.kt
│   │       ├── BootReceiver.kt
│   │       └── BatteryReceiver.kt
│   └── res/
│       ├── layout/activity_main.xml    ← 深藍主題設定頁
│       └── xml/accessibility_service_config.xml
```

---

## 🚀 Android Studio 開啟步驟

1. 打開 Android Studio
2. `File → Open` → 選擇此 `CyberNavi` 資料夾
3. 等待 Gradle Sync 完成（需要下載依賴，約需幾分鐘）
4. 如果 Gradle 報錯，點擊 `File → Sync Project with Gradle Files`

---

## 📱 在 Emulator 測試步驟

1. `Tools → Device Manager` → 創建一個 Pixel 8 / API 34 的 Emulator
2. 點擊 ▶ Run 安裝 App
3. 在 Emulator 中：
   - 打開 App，點擊「開啟懸浮視窗權限」
   - 系統設定 → 無障礙 → 雷霆 AI 感知服務 → 開啟
   - 返回 App，點擊「⚡ 啟動雷霆」
4. 雷霆小圓形圖示應該出現在螢幕右上角！

---

## ⚡ Phase 1 功能狀態

| 功能 | 狀態 | 備注 |
|------|------|------|
| 懸浮視窗顯示 | ✅ 完成 | 可拖動，點擊切換氣泡 |
| 雷霆角色動畫 | ✅ 完成 | 閃電圓形，有眨眼/嘴巴動畫 |
| 電量狀態感知 | ✅ 完成 | 低電/充電時改變外觀 |
| Accessibility 截獲 | ✅ 完成 | 監聽 Samsung AI、通知等 |
| 廣東話轉譯（規則）| ✅ 完成 | 基礎書面語→口語轉換 |
| 開機自動啟動 | ✅ 完成 | BootReceiver |
| Live2D 角色 | 🔲 Phase 2 | 需購買 Live2D SDK |
| LLM API 轉譯 | 🔲 Phase 2 | 規則引擎已預留接口 |
| 記憶系統 | 🔲 Phase 2 | ChromaDB 家用伺服器 |

---

## 🔧 Phase 2 升級路徑

### 升級 Live2D
1. 申請 Live2D Cubism SDK for Android（免費）
2. 將 `ThunderAvatarCanvas` 替換為 Live2D 的 `LAppView`
3. 位置：`ThunderFloatingView.kt` 第 50-60 行

### 升級 LLM 轉譯
1. 在 `ThunderPersona.kt` 底部有預留的 `translateWithLLM()` 接口
2. 加入 OpenAI API key 到 `local.properties`：
   ```
   OPENAI_API_KEY=sk-xxx
   ```
3. 取消 `translateWithLLM()` 的註釋

---

## 📝 已知限制（Phase 1）

- Samsung Emulator 無法測試 Galaxy AI 截獲（需要真實 Samsung 裝置）
- 廣東話轉換用規則引擎，部分句子可能轉換不自然
- 角色係簡單幾何圖形，需 Live2D 才有真實 2D 角色

---

*Project Cyber-Navi v0.1.0-alpha | Phase 1 | 2026.03*
