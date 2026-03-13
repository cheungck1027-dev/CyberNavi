# Project Cyber-Navi ⚡
## 靈魂寄生型 AI 領航員 — Phase 1+2 完成

> **當前版本**: v0.1.0-alpha | Phase 1+2 Ready
> 雷霆寄生喺你嘅 Samsung Galaxy AI，用廣東話口語同你溝通。

---

## 📂 項目結構

```
CyberNavi/                              ← Android App (本 Repo)
├── app/src/main/
│   ├── java/com/cybernavi/thunder/
│   │   ├── CyberNaviApp.kt              ← Application 入口（DataStore 初始化）
│   │   ├── MainActivity.kt              ← 設定頁面（伺服器 URL + 測試連線）
│   │   ├── network/
│   │   │   └── ThunderServerClient.kt  ← Phase 2 家用伺服器 HTTP 客戶端
│   │   ├── service/
│   │   │   ├── FloatingWindowService.kt ← 懸浮視窗服務（核心）
│   │   │   └── ThunderAccessibilityService.kt ← 截獲系統AI輸出 + 呼叫翻譯
│   │   ├── settings/
│   │   │   └── SettingsStore.kt        ← DataStore 持久化（伺服器 URL）
│   │   ├── ui/
│   │   │   └── ThunderFloatingView.kt  ← 雷霆角色UI + 對話氣泡
│   │   ├── persona/
│   │   │   └── ThunderPersona.kt       ← 廣東話轉譯引擎（Phase 2: LLM優先）
│   │   └── util/
│   │       ├── PermissionHelper.kt
│   │       ├── BootReceiver.kt
│   │       └── BatteryReceiver.kt
│   └── res/
│       ├── layout/activity_main.xml    ← 深藍主題設定頁（含伺服器設定區）
│       └── xml/accessibility_service_config.xml

server/                                 ← Phase 2 家用伺服器 (FastAPI + ChromaDB)
├── main.py                             ← FastAPI 應用入口
├── api/routes.py                       ← /translate /memory /persona 端點
├── persona/thunder_llm.py              ← Claude/GPT 推理引擎 + 廣東話 Prompt
├── memory/chroma_store.py              ← ChromaDB 向量記憶庫
├── requirements.txt
├── start.sh                            ← 一鍵啟動腳本
└── .env.template                       ← 環境變量範本
```

---

## ⚡ Phase 功能狀態

| 功能 | 狀態 | 備注 |
|------|------|------|
| 懸浮視窗顯示 | ✅ 完成 | 可拖動，點擊切換氣泡 |
| 雷霆角色動畫 | ✅ 完成 | 閃電圓形，有眨眼/嘴巴動畫 |
| 電量狀態感知 | ✅ 完成 | 低電/充電時改變外觀 |
| Accessibility 截獲 | ✅ 完成 | 監聽 Samsung AI、通知等 |
| 廣東話轉譯（規則） | ✅ 完成 | Phase 1 本地規則引擎 |
| 開機自動啟動 | ✅ 完成 | BootReceiver |
| **DataStore 伺服器 URL 持久化** | ✅ Phase 2 | App 重啟後保留設定 |
| **家用伺服器 UI（MainActivity）** | ✅ Phase 2 | 填 URL / 測試連線 / 顯示狀態 |
| **LLM 翻譯（伺服器優先）** | ✅ Phase 2 | Claude/GPT → 降級至本地規則 |
| **記憶系統（ChromaDB）** | ✅ Phase 2 | 語義搜索歷史交互 |
| FastAPI 家用伺服器 | ✅ Phase 2 | /translate /memory /health |
| Live2D 角色 | 🔲 Phase 3 | 需購買 Live2D SDK |
| Tailscale VPN 部署 | 🔲 Phase 3 | 從外網訪問家用伺服器 |

---

## 🚀 Android Studio 開啟步驟

1. 打開 Android Studio
2. `File → Open` → 選擇此 `CyberNavi` 資料夾
3. 等待 Gradle Sync 完成（需要下載依賴，約需幾分鐘）
4. 如果 Gradle 報錯，點擊 `File → Sync Project with Gradle Files`

---

## 📱 安裝測試步驟

1. `Tools → Device Manager` → 創建一個 Pixel 8 / API 34 的 Emulator
2. 點擊 ▶ Run 安裝 App
3. 在裝置中：
   - 打開 App，點擊「開啟懸浮視窗權限」
   - 系統設定 → 無障礙 → 雷霆 AI 感知服務 → 開啟
   - 返回 App，點擊「⚡ 啟動雷霆」
4. 雷霆小圓形圖示應該出現在螢幕右上角！

---

## 🖥️ Phase 2 家用伺服器設定

### 1. 安裝依賴

```bash
cd server/
cp .env.template .env
# 編輯 .env，填入你的 API Key：
# ANTHROPIC_API_KEY=sk-ant-xxxxx
# 或 OPENAI_API_KEY=sk-xxxxx

pip install -r requirements.txt
```

### 2. 啟動伺服器

```bash
bash start.sh
# 或直接：
python main.py

# 伺服器會在 http://0.0.0.0:8765 啟動
# API 文檔：http://localhost:8765/docs
```

### 3. Android App 連接伺服器

1. 打開 CyberNavi App → 「Phase 2 家用伺服器」區域
2. 填入伺服器 IP：`http://<你電腦的IP>:8765`
   - 同一WiFi下：用內網 IP（如 `http://192.168.1.100:8765`）
   - 外網訪問：用 [Tailscale](https://tailscale.com/) IP（如 `http://100.x.x.x:8765`）
3. 點擊「💾 儲存」→「🔗 測試連線」
4. 狀態顯示「✅ 伺服器在線」即代表連接成功

### 4. Phase 2 工作流程

```
Samsung Galaxy AI 輸出
        ↓
ThunderAccessibilityService 截獲文字
        ↓
ThunderPersona.translate()
   ├── 伺服器在線 → POST /api/v1/translate → Claude/GPT LLM 回應
   │                    └── ChromaDB 搜索相關記憶 → 豐富上下文
   └── 伺服器離線 → Phase 1 本地規則引擎（廣東話口語轉換）
        ↓
懸浮視窗顯示雷霆回應
```

---

## 🔧 環境變量（server/.env）

| 變量 | 說明 | 預設值 |
|------|------|--------|
| `LLM_PROVIDER` | LLM 供應商 | `anthropic` |
| `LLM_MODEL` | 模型名稱 | `claude-3-5-sonnet-20241022` |
| `ANTHROPIC_API_KEY` | Claude API Key | - |
| `OPENAI_API_KEY` | OpenAI API Key（可選）| - |
| `SERVER_PORT` | 伺服器端口 | `8765` |
| `CHROMA_PERSIST_DIR` | ChromaDB 數據目錄 | `./chroma_db` |

---

## 📝 已知限制

- Samsung Emulator 無法測試 Galaxy AI 截獲（需要真實 Samsung 裝置）
- Phase 2 LLM 翻譯需要家用伺服器在線 + 有效 API Key
- 角色係簡單幾何圖形，需 Live2D 才有真實 2D 角色（Phase 3）

---

*Project Cyber-Navi v0.1.0-alpha | Phase 1+2 Ready | 2026.03*
